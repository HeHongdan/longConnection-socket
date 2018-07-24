package com.hehongdan.socket;

import android.app.Activity;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.hehongdan.socket.broad.SocketBroadcastReceiver;
import com.hehongdan.socket.interfaces.SocketCallback;

public class MainActivity extends Activity {

    /** Socket长链接核心类 */
    private SocketBroadcastReceiver mHeartBeatBroadCast;
    /** 历史消息 */
    private TextView txtMsg;
    /** 发送按钮 */
    private Button send;
    /** 消息输入框 */
    private EditText etMsg;
    /** 需要发送字符串 */
    private String content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_socket);
        socketTest();
    }

    @Override
    protected void onResume() {
        super.onResume();

        //注册广播（启动心跳、监听收到消息）
        IntentFilter mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(SocketBroadcastReceiver.HEART_BEAT_ACTION);
        mIntentFilter.addAction(SocketBroadcastReceiver.MESSAGE_ACTION);
        registerReceiver(mHeartBeatBroadCast, mIntentFilter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mHeartBeatBroadCast);
    }

    /**
     * socket测试
     */
    private void socketTest() {
        txtMsg = (TextView) findViewById(R.id.txt1);
        //TextView支持内容滑动
        txtMsg.setMovementMethod(ScrollingMovementMethod.getInstance());
        send = (Button) findViewById(R.id.send);
        etMsg = (EditText) findViewById(R.id.ed1);
        send.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                content = etMsg.getText().toString();
                if (TextUtils.isEmpty(content)) {
                    Toast.makeText(MainActivity.this, "发送的消息不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                boolean sendState = mHeartBeatBroadCast.sendMsg(content);
                if (sendState) {
                    Toast.makeText(getApplicationContext(), "发送成功！", Toast.LENGTH_SHORT).show();
                    setMessages("APP客户端发送消息： " + content);
                    etMsg.setText("");
                } else {
                    Toast.makeText(getApplicationContext(), "发送失败！", Toast.LENGTH_SHORT).show();
                }
            }
        });

        //注册Socket广播
        mHeartBeatBroadCast = new SocketBroadcastReceiver(this,"192.168.1.109", 1234);
        setMessages("正在连接到服务器(" + mHeartBeatBroadCast.host + ":" + mHeartBeatBroadCast.port + ")...");
        mHeartBeatBroadCast.setSocketCallback(new SocketCallback() {
            @Override
            public void connected() {
                Log.i("ysl", "服务器连接了...");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setMessages("已连接到服务器...");
                    }
                });
            }

            @Override
            public void disConnected() {
                Log.i("ysl", "服务器断开了...");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setMessages("服务器断开，自动重连中...");
                    }
                });
                //再次进入Socket初始化连接
                mHeartBeatBroadCast.initSocket("192.168.1.109", 30003);
            }

            @Override
            public void receiveMessage(String message) {
                Log.i("ysl", "收到新消息:" + message);
                setMessages("收到服务端消息：" + message);
            }
        });
    }

    /**
     * 追加TextView文本
     *
     * @param message
     */
    public void setMessages(String message) {
        txtMsg.append(message + "\n");
        int offset = txtMsg.getLineCount() * txtMsg.getLineHeight();
        if (offset > txtMsg.getHeight()) {
            //自动滚动到最后一条消息
            txtMsg.scrollTo(0, offset - txtMsg.getHeight());
        }
    }


}
