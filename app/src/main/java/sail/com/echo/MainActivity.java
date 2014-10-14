package sail.com.echo;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;


public class MainActivity extends Activity implements AudioRecorder.Callback {
  Button startButton;
  TextView logTextView;
  AudioTrack backgroundAudioTrack;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    startButton = (Button) findViewById(R.id.startButton);
    logTextView = (TextView) findViewById(R.id.logTextView);
    readBackgroundAudioTrack();

    startButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        startEchoTest();
      }
    });
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.main, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();
    if (id == R.id.action_settings) {
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private void startEchoTest() {
    log("Recording");
    startButton.setEnabled(false);
    AudioRecorder audioRecorder = new AudioRecorder(this, this);
    audioRecorder.start();
    backgroundAudioTrack.reloadStaticData();
    backgroundAudioTrack.setPlaybackHeadPosition(0);
    backgroundAudioTrack.play();
  }

  private void log(String msg) {
    String logText = logTextView.getText() + "\n" + msg;
    logTextView.setText(logText);
  }

  @Override
  public void onRecordingStopped(AudioTrack audioTrack) {
    log("Playing");
    startButton.setEnabled(true);
    backgroundAudioTrack.stop();
    audioTrack.play();
  }

  private void readBackgroundAudioTrack() {
    try {
      InputStream stream = new ObjectInputStream(new BufferedInputStream(
          getResources().openRawResource(R.raw.audio_data)));
      try {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        byte buffer[] = new byte[1024];
        while (true) {
          int readSize = stream.read(buffer);
          if (readSize == -1) {
            break;
          }
          byteStream.write(buffer, 0, readSize);
        }
        backgroundAudioTrack = new AudioTrack(AudioManager.STREAM_VOICE_CALL,
            AudioRecorder.SAMPLE_RATE, AudioRecorder.CHANNEL_CONFIG_OUT, AudioRecorder.AUDIO_FORMAT,
            byteStream.size(), AudioTrack.MODE_STATIC);
        backgroundAudioTrack.write(byteStream.toByteArray(), 0, byteStream.size());
      } finally {
        stream.close();
      }
    } catch (Exception e) {
      Log.e("Echo", "error", e);
      log("read audio_track failed with error: " + e);
    }
  }
}