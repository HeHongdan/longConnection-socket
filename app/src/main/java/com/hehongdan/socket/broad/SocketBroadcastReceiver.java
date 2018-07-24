package com.hehongdan.socket.broad;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import com.hehongdan.socket.interfaces.SocketCallback;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Socket长链接核心类（自定义广播）
 *
 * 1.定时与服务器心跳保持(时间可以自定义)
 * 2.断线自动重连
 * 3.连接成功、断开连接、收到消息回调处理
 * 4.消息发送状态获取(成功true or 失败false)
 * 5.注册广播
 * @author 爱学习di年轻人
 *  2016年11月22日 下午4:23:17
 */
public class SocketBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "SocketBroadcastReceiver";
    /** 心跳检测时间 */
    private static final long HEART_BEAT_RATE = 10 * 1000;
    /** 主机IP地址 */
    public String host = "192.168.1.109";
    /** 端口号 */
    public int port = 30003;
    /** 超时设置 **/
    public static final int SOCKET_TIME_OUT = 10 * 1000;
    /** 消息广播 */
    public static final String MESSAGE_ACTION = "com.ysl.message_ACTION";
    /** 心跳广播 */
    public static final String HEART_BEAT_ACTION = "com.ysl.heart_beat_ACTION";
    /** 线程池 **/
    private ExecutorService executorService = Executors.newFixedThreadPool(5);
    /** 为了节省开销：如果最后发送时间间隔不超过心跳时间则不发心跳 */
    private long sendTime = 0L;
    /** 接收消息线程 */
    private ReadThread mReadThread;
    /** 上下文 */
    private Context context;
    /** 套接字 */
    private Socket socket;
    /** 是否处于连接状态 */
    private boolean isConnected = true;
    /** 回调 */
    private SocketCallback callback;

    /**
     * 长链接核心类（构造并初始化Socket）
     *
     * @param context
     */
    /*public SocketBroadcastReceiver(Context context) {
        this.context = context;
        //initSocket();
    }*/

    /**
     * 长链接核心类（构造并初始化Socket）
     *
     * @param context   上下文
     * @param host      主机IP
     * @param port      主机端口
     */
    public SocketBroadcastReceiver(Context context, String host, int port) {
        this.context = context;
        this.host = host;
        this.port = port;
        initSocket(host, port);
    }

    /**
     * 设置Socket状态回调
     *
     * @param callback
     */
    public void setSocketCallback(SocketCallback callback) {
        this.callback = callback;
    }

    /**
     * 初始化Socake
     */
    public void initSocket(final String host, final int port) {
        //线程池执行
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    socket = new Socket();
                    SocketAddress socAddress = new InetSocketAddress(host, port);
                    //设置连接和超时时间
                    socket.connect(socAddress, SOCKET_TIME_OUT);
                    //FIXME 缺少链接成功的判断逻辑
                    if (callback != null) {
                        isConnected = true;
                        callback.connected();
                    }
                    //启动接收线程
                    mReadThread = new ReadThread();
                    mReadThread.start();
                    //按频率执行心跳线程（初始化成功后，就准备发送心跳包）
                    mHandler.postDelayed(heartBeatRunnable, HEART_BEAT_RATE);
                } catch (UnknownHostException e) {
                    //链接失败（无法知道主机）
                    if (callback != null) {
                        isConnected = false;
                        callback.disConnected();
                    }
                    e.printStackTrace();
                } catch (IOException e) {
                    //链接失败
                    if (callback != null) {
                        isConnected = false;
                        callback.disConnected();
                    }
                    e.printStackTrace();//FIXME 应该处理异常（不抛出）
                }
            }
        });

    }

    /**
     * 重写接收广播方法
     *
     * @param context   上下文
     * @param intent    广播意图
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        // 消息广播
        if (action.equals(MESSAGE_ACTION)) {
            String stringExtra = intent.getStringExtra("message");
//			Log.i(TAG, "收到服务器消息：" + stringExtra);
            if (callback != null) {
                callback.receiveMessage(stringExtra);
            }
            // 心跳广播
        } else if (action.equals(HEART_BEAT_ACTION)) {
            Log.i("ysl", "收到服务器正常心跳。");
        }

    }

    /**
     * 发送心跳包线程
     */
    private Handler mHandler = new Handler();
    /** 心跳线程 */
    private Runnable heartBeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (System.currentTimeMillis() - sendTime >= HEART_BEAT_RATE) {
                // 可以随意与服务器定义好内容。。。
                boolean isSuccess = sendMsg("发送到服务器的内容");
                // 如果发送不成功重连
                if (!isSuccess) {
                    //removeCallbacks：删除Runnable并停止线程运行
                    mHandler.removeCallbacks(heartBeatRunnable);
                    mReadThread.release();
                    //releaseLastSocket(socket);//release方法已执行
                    if (callback != null) {
                        callback.disConnected();
                    }
                }
            }
            //postDelayed：延迟多少毫秒后运行
            mHandler.postDelayed(this, HEART_BEAT_RATE);
        }
    };

    /**
     * 发送消息
     *
     * @param msg
     * @return
     */
    public boolean sendMsg(String msg) {
        if (null == socket) {
            return false;
        }
        try {
            //isOutputShutdown：是否关闭套接字连接的半写状态
            if (!socket.isClosed() && !socket.isOutputShutdown() && isConnected) {
                OutputStream os = socket.getOutputStream();
                String message = msg + "\r\n";
                os.write(message.getBytes());
                os.flush();
                // 每次发送成功数据，就改一下最后成功发送的时间，节省心跳间隔时间
                sendTime = System.currentTimeMillis();
                Log.i(TAG, "发送成功的时间：" + sendTime);
            } else {
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 释放socket
     *
     * @param mSocket
     */
    private void releaseLastSocket(Socket mSocket) {
        try {
            if (null != mSocket) {
                if (!mSocket.isClosed()) {
                    mSocket.close();
                }
                mSocket = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 接收消息线程
     */
    public class ReadThread extends Thread {
        private boolean isStart = true;
        /** 释放线程（Socket） */
        public void release() {
            isStart = false;
            releaseLastSocket(socket);
        }

        @SuppressLint("NewApi")
        @Override
        public void run() {
            if (null != socket && isConnected) {
                try {
                    InputStream is = socket.getInputStream();
                    //缓存空间
                    byte[] buffer = new byte[1024 * 4];
                    int length = 0;
                    while (!socket.isClosed() && !socket.isInputShutdown() && isStart && ((length = is.read(buffer)) != -1)) {
                        //isConnected = true;//FIXME 前面第6重复
                        if (length > 0) {
                            String message = new String(Arrays.copyOf(buffer, length)).trim();
                            // 收到服务器过来的消息，就通过Broadcast发送出去
                            if (message.equals("ok")) {//FIXME 成功的标记
                                // 处理心跳
                                Intent intent = new Intent(HEART_BEAT_ACTION);
                                context.sendBroadcast(intent);
                                // 其他消息回复
                            } else {
                                // 处理回复
                                Intent intent = new Intent(MESSAGE_ACTION);
                                intent.putExtra("message", message);
                                context.sendBroadcast(intent);
                            }
                        }
                    }
                    isConnected = false;
                    if (callback != null) {
                        callback.disConnected();
                    }
                } catch (IOException e) {
                    isConnected = false;
                    if (callback != null) {
                        callback.disConnected();
                    }
                    e.printStackTrace();
                    Log.i("ysl", "已经断开IOException...");
                }
            }
        }
    }
}
