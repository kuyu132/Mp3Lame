package com.lame.mp3.utils;

import android.media.AudioRecord;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by kuyu on 2017/4/11.
 */

public class DataEncodeThread extends HandlerThread implements AudioRecord.OnRecordPositionUpdateListener {
    private static final String TAG = "DataEncodeThread";
    private StopHandler mHandler;
    private static final int PROCESS_STOP = 1;
    private byte[] mMp3Buffer;
    private FileOutputStream mFileOutputStream;
    private static WeakReference<MP3Recorder> mp3RecorderRef;

    private static class StopHandler extends Handler {

        private DataEncodeThread encodeThread;

        public StopHandler(Looper looper, DataEncodeThread encodeThread) {
            super(looper);
            this.encodeThread = encodeThread;
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == PROCESS_STOP) {
                //处理缓冲区中的数据
                while (encodeThread.processData() > 0) ;
                // Cancel any event left in the queue
                removeCallbacksAndMessages(null);
                encodeThread.flushAndRelease();
                if (mp3RecorderRef != null) mp3RecorderRef.get().callBack();
                getLooper().quit();
            }
        }
    }

    /**
     * Constructor
     *
     * @param file file
     * @param bufferSize bufferSize
     * @throws FileNotFoundException file not found
     */

    /**
     * 2017-4-28
     *
     * @throws FileNotFoundException
     */
    public DataEncodeThread(MP3Recorder mp3Recorder, File file, int bufferSize) throws FileNotFoundException {
        super(TAG);
        this.mFileOutputStream = new FileOutputStream(file);
        mMp3Buffer = new byte[(int) (7200 + (bufferSize * 2 * 1.25))];
        mp3RecorderRef = new WeakReference<MP3Recorder>(mp3Recorder);
    }

    public DataEncodeThread(File file, int bufferSize) throws FileNotFoundException {
        super(TAG);
        this.mFileOutputStream = new FileOutputStream(file);
        mMp3Buffer = new byte[(int) (7200 + (bufferSize * 2 * 1.25))];
    }

    @Override
    public synchronized void start() {
        super.start();
        mHandler = new StopHandler(getLooper(), this);
    }

    private void check() {
        if (mHandler == null) {
            throw new IllegalStateException();
        }
    }

    public void sendStopMessage() {
        check();
        mHandler.sendEmptyMessage(PROCESS_STOP);
    }

    public Handler getHandler() {
        check();
        return mHandler;
    }

    @Override
    public void onMarkerReached(AudioRecord recorder) {
        // Do nothing
    }

    @Override
    public void onPeriodicNotification(AudioRecord recorder) {
        processData();
    }

    /**
     * 从缓冲区中读取并处理数据，使用lame编码MP3
     *
     * @return 从缓冲区中读取的数据的长度
     * 缓冲区中没有数据时返回0
     */
    private int processData() {
        if (mTasks.size() > 0) {
            Task task = mTasks.remove(0);
            short[] buffer = task.getData();
            int readSize = task.getReadSize();
            int encodedSize = LameUtil.encode(buffer, buffer, readSize, mMp3Buffer);
            if (encodedSize > 0) {
                try {
                    mFileOutputStream.write(mMp3Buffer, 0, encodedSize);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return readSize;
        }
        return 0;
    }

    /**
     * Flush all data left in lame buffer to file
     */
    private void flushAndRelease() {
        //将MP3结尾信息写入buffer中
        final int flushResult = LameUtil.flush(mMp3Buffer);
        if (flushResult > 0) {
            try {
                mFileOutputStream.write(mMp3Buffer, 0, flushResult);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (mFileOutputStream != null) {
                    try {
                        mFileOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                LameUtil.close();
            }
        }
    }

    private List<Task> mTasks = Collections.synchronizedList(new ArrayList<Task>());

    public void addTask(short[] rawData, int readSize) {
        mTasks.add(new Task(rawData, readSize));
    }

    private class Task {
        private short[] rawData;
        private int readSize;

        public Task(short[] rawData, int readSize) {
            this.rawData = rawData.clone();
            this.readSize = readSize;
        }

        public short[] getData() {
            return rawData;
        }

        public int getReadSize() {
            return readSize;
        }
    }

    public void release() {
        if (mHandler != null) mHandler.removeCallbacksAndMessages(null);
        quit();
    }
}
