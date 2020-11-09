import com.intelligt.modbus.jlibmodbus.exception.ModbusIOException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusNumberException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusProtocolException;
import com.intelligt.modbus.jlibmodbus.master.ModbusMaster;
import com.intelligt.modbus.jlibmodbus.master.ModbusMasterFactory;
import com.intelligt.modbus.jlibmodbus.serial.*;
import com.intelligt.modbus.jlibmodbus.tcp.TcpParameters;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class ModbusTest {

    public static void main(String[] args) {
        String ip = "10.99.10.135";
        String[] registers = {"180/16"};
        String[] readData = {"0/float", "2/float", "4/float", "6/float", "8/float", "10/float", "12/float", "14/float"};

        System.out.println(ip + " " + Arrays.toString(registers));
        try {
            try {
                TcpParameters tcpParameter = new TcpParameters();
                tcpParameter.setHost(InetAddress.getByName(ip));
                tcpParameter.setPort(502);
                tcpParameter.setKeepAlive(true);

                SerialParameters serialParameter = new SerialParameters();
                serialParameter.setBaudRate(SerialPort.BaudRate.BAUD_RATE_115200);
                serialParameter.setDataBits(8);
                serialParameter.setParity(SerialPort.Parity.NONE);
                serialParameter.setStopBits(1);

                SerialUtils.setSerialPortFactory(new SerialPortFactoryTcpClient(tcpParameter));
                ModbusMaster master = ModbusMasterFactory.createModbusMasterRTU(serialParameter);
                master.setResponseTimeout(10000);
                master.connect();

                int slaveId = 1;

                System.out.println("id " + slaveId);

                for (String register: registers) {
                    int offset = Integer.parseInt(register.split("/")[0]);
                    int quantity = Integer.parseInt(register.split("/")[1]);
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

                    for (String data: readData) {
                        int index = Integer.parseInt(data.split("/")[0]);

                        switch (data.split("/")[1]) {
                            case "float": {
                                ByteBuffer buffer = ByteBuffer.allocate(4)
                                        .putShort((short) registerValues[index + 1])
                                        .putShort((short) registerValues[index]);

                                System.out.println(buffer.order(ByteOrder.BIG_ENDIAN).getFloat(0));
                            }
                        }
                    }
                }
                master.disconnect();
            } catch (SerialPortException | UnknownHostException | ModbusIOException | ModbusNumberException |
                    ModbusProtocolException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
