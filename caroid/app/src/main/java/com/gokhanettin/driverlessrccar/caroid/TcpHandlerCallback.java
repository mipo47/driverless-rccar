package com.gokhanettin.driverlessrccar.caroid;

import android.os.Handler;
import android.os.Message;

abstract class TcpHandlerCallback implements Handler.Callback {
    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case TcpClient.MESSAGE_CONNECTION_STATE_CHANGE:
                onConnectionStateChanged(msg.arg1);
                break;
            case TcpClient.MESSAGE_RECEIVE:
                TcpInput in = (TcpInput) msg.obj;
                onReceived(in);
                break;
            case TcpClient.MESSAGE_SEND:
                TcpOutput out = (TcpOutput) msg.obj;
                onSent(out);
                break;
            case TcpClient.MESSAGE_CONNECTION_ESTABLISHED:
                String serverAddress = msg.getData().getString(TcpClient.SERVER_ADDRESS);
                onConnectionEstablished(serverAddress);
                break;
            case TcpClient.MESSAGE_CONNECTION_ERROR:
                String error = msg.getData().getString(TcpClient.CONNECTION_ERROR);
                onConnectionError(error);
                break;
        }
        return true;
    }

    protected abstract void onConnectionStateChanged(int newState);
    protected abstract void onReceived(TcpInput input);
    protected abstract void onSent(TcpOutput output);
    protected abstract void onConnectionEstablished(String serverAddress);
    protected abstract void onConnectionError(String error);
}
