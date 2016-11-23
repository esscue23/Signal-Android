package org.thoughtcrime.securesms;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.app.Activity;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import org.thoughtcrime.securesms.qr.QrCode;
import org.thoughtcrime.securesms.service.LinkingService;
import org.thoughtcrime.securesms.util.ViewUtil;

public class LinkingProgressActivity extends Activity {
  private ServiceConnection serviceConnection = new LinkingServiceConnection();
  private LinkingService linkingService;
  private View container;
  private ImageView qrCode;

  private BroadcastReceiver messageReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      Log.d("LinkingProgressActivity", "### " + intent);
      switch (intent.getAction()) {
        case LinkingService.LINKING_EVENT:
          shutdownService();
          startActivity(new Intent(LinkingProgressActivity.this, ConversationListActivity.class));
          finish();
          break;
        case LinkingService.LINKING_PUBKEY:
          Log.d("LinkingProgressActivity", "recv pubkey " + intent.getStringExtra(LinkingService.LINKING_PUBKEY));
          Bitmap qrCodeBitmap = QrCode.create(intent.getStringExtra(LinkingService.LINKING_PUBKEY));
          qrCode.setImageBitmap(qrCodeBitmap);
          break;
      }
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    Log.d("LinkingProgressActivity", "onCreate");
    super.onCreate(savedInstanceState);
    setContentView(R.layout.linking_progress_activity);
    qrCode = (ImageView) findViewById(R.id.linking_qr_code);


    LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, new IntentFilter(LinkingService.LINKING_EVENT));
    LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, new IntentFilter(LinkingService.LINKING_PUBKEY));

    Intent intent = new Intent(this, LinkingService.class);
    bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    startService(intent);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    shutdownService();
  }

  private void shutdownServiceBinding() {
    if (serviceConnection != null) {
      unbindService(serviceConnection);
      serviceConnection = null;
    }
  }

  private void shutdownService() {
    if (linkingService != null) {
      linkingService.shutdown();
      linkingService = null;
    }

    shutdownServiceBinding();
    Intent serviceIntent = new Intent(this, LinkingService.class);
    stopService(serviceIntent);
  }

  private class LinkingServiceConnection implements ServiceConnection {
    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
    }
  }
}
