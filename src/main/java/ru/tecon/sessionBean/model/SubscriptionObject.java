package ru.tecon.sessionBean.model;

/**
 * Класс описывающий информацию о подписанном объекте
 */
public class SubscriptionObject {

    private String id;
    private String objectName;
    private String serverName;

    public SubscriptionObject(String id, String objectName, String serverName) {
        this.id = id;
        this.objectName = objectName;
        this.serverName = serverName;
    }

    public String getId() {
        return id;
    }

    public String getObjectName() {
        return objectName;
    }

    public String getServerName() {
        return serverName;
    }

    @Override
    public String toString() {
        return "SubscriptionObject{" +
                "id='" + id + '\'' +
                ", objectName='" + objectName + '\'' +
                ", serverName='" + serverName + '\'' +
                '}';
    }
}
