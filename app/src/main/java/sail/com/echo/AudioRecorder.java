package sail.com.echo;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;

final class AudioRecorder {
  private static final String TAG = "AudioRecorder";
  static final int SAMPLE_RATE = 44100;
  static final int CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO;
  static final int CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO;
  static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

  private final Context context;
  private final Callback callback;
  private AudioRecord audioRecord;
  private Thread audioThread;
  private AcousticEchoCanceler echoCanceler;

  interface Callback {
    void onRecordingStopped(AudioTrack audioTrack);
  }

  AudioRecorder(Context context, Callback callback) {
    this.context = context;
    this.callback = callback;
  }

  void start() {
    getAudioFocus();
    final int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN,
        AUDIO_FORMAT);
    audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT, minBufferSize);
    createEffects(audioRecord.getAudioSessionId());
    audioRecord.startRecording();

    audioThread = new Thread(new Runnable() {
      @Override
      public void run() {
        recordAudioOnThread(minBufferSize);
      }
    }, "Audio Thread");
    audioThread.start();
  }

  private void recordAudioOnThread(int minBufferSize) {
    byte buffer[] = new byte[300 * 1024];
    int offset = 0;
    while (offset < buffer.length) {
      int remainder = buffer.length - offset;
      int readSize = Math.min(minBufferSize, remainder);
      if (readSize == 0) {
        break;
      }
      Log.d(TAG, "offset: " + offset + ", reading" + readSize);
      readSize = audioRecord.read(buffer, offset, readSize);
      Log.d(TAG, "read: " + readSize);
      if (readSize == AudioRecord.ERROR_INVALID_OPERATION) {
        Log.e(TAG, "AudioRecord.read returned ERROR_INVALID_OPERATION");
        continue;
      }
      if (readSize == AudioRecord.ERROR_BAD_VALUE) {
        Log.e(TAG, "AudioRecord.read returned ERROR_BAD_VALUE");
        break;
      }
      offset += readSize;
    }

    audioRecord.stop();
    audioRecord.release();

    // writeAudioDataToDisk(buffer, offset);

    final AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_VOICE_CALL, SAMPLE_RATE,
        CHANNEL_CONFIG_OUT, AUDIO_FORMAT, offset, AudioTrack.MODE_STATIC);
    audioTrack.write(buffer, 0, offset);
    Handler handler = new Handler(context.getMainLooper());
    handler.post(new Runnable() {
      @Override
      public void run() {
        onRecordingStopped(audioTrack);
      }
    });
  }

  private void createEffects(int sessionId) {
    echoCanceler = AcousticEchoCanceler.create(sessionId);
    echoCanceler.setEnabled(true);
  }

  private void getAudioFocus() {
    AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
    audioManager.setSpeakerphoneOn(true);
    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
  }

  private void abandonAudioFocus() {
    AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    audioManager.abandonAudioFocus(null);
  }

  private void onRecordingStopped(AudioTrack audioTrack) {
    audioRecord = null;
    audioThread = null;
    echoCanceler = null;
    abandonAudioFocus();
    callback.onRecordingStopped(audioTrack);
  }

  private void writeAudioDataToDisk(byte buffer[], int len) {
    try {
      File file = new File(context.getCacheDir(), "audio_data");
      ObjectOutputStream stream = new ObjectOutputStream(new BufferedOutputStream(
          new FileOutputStream(file)));
      try {
        stream.write(buffer, 0, len);
        stream.flush();
      } finally {
        stream.close();
      }
    } catch (Exception e) {
      Log.e(TAG, "Write failed", e);
    }
  }
}