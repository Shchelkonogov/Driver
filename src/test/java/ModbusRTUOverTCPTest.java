import com.intelligt.modbus.jlibmodbus.exception.ModbusIOException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusNumberException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusProtocolException;
import com.intelligt.modbus.jlibmodbus.master.ModbusMaster;
import com.intelligt.modbus.jlibmodbus.master.ModbusMasterFactory;
import com.intelligt.modbus.jlibmodbus.msg.request.ReadHoldingRegistersRequest;
import com.intelligt.modbus.jlibmodbus.msg.response.ReadHoldingRegistersResponse;
import com.intelligt.modbus.jlibmodbus.serial.*;
import com.intelligt.modbus.jlibmodbus.tcp.TcpParameters;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class ModbusRTUOverTCPTest {

    public static void main(String[] args) {
        try {
            TcpParameters tcpParameter = new TcpParameters();
            InetAddress host = InetAddress.getLocalHost();
            tcpParameter.setHost(host);
            tcpParameter.setPort(502);
            tcpParameter.setKeepAlive(true);
//            SerialUtils.setSerialPortFactory(new SerialPortFactoryTcpServer(tcpParameter));

            SerialParameters serialParameter = new SerialParameters();
            serialParameter.setBaudRate(SerialPort.BaudRate.BAUD_RATE_9600);

            SerialUtils.setSerialPortFactory(new SerialPortFactoryTcpClient(tcpParameter));
            ModbusMaster master = ModbusMasterFactory.createModbusMasterRTU(serialParameter);
            master.connect();

            int slaveId = 1;
            int offset = 0;
            int quantity = 10;
            //you can invoke #connect method manually, otherwise it'll be invoked automatically
            // at next string we receive ten registers from a slave with id of 1 at offset of 0.
            int[] registerValues = master.readHoldingRegisters(slaveId, offset, quantity);
            // print values
            int address = offset;
            for (int value : registerValues) {
                System.out.println("Address: " + address++ + ", Value: " + value);
            }

            System.out.println("Read " + quantity + " HoldingRegisters start from " + offset);
            System.out.println();

            /*
             * The same thing using a request
             */
            ReadHoldingRegistersRequest readRequest = new ReadHoldingRegistersRequest();
            readRequest.setServerAddress(slaveId);
            readRequest.setStartAddress(offset);
            readRequest.setQuantity(quantity);

            master.processRequest(readRequest);
            ReadHoldingRegistersResponse response = (ReadHoldingRegistersResponse)readRequest.getResponse();

            for (int value : response.getHoldingRegisters()) {
                System.out.println("Address: " + address++ + ", Value: " + value);
            }

            System.out.println("Read " + quantity + " HoldingRegisters start from " + offset);
            System.out.println();

            master.disconnect();
        } catch (SerialPortException | UnknownHostException | ModbusIOException | ModbusNumberException |
                ModbusProtocolException e) {
            e.printStackTrace();
        }
    }
}
