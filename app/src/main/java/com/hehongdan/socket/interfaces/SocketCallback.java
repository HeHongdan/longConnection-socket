package com.hehongdan.socket.interfaces;

/**
 * Socket回调函数
 */
public interface SocketCallback {
    /**
     * 连接上
     */
    void connected();

    /**
     * 断开连接
     */
    void disConnected();

    /**
     * 收到消息
     * @param message
     */
    void receiveMessage(String message);
}
