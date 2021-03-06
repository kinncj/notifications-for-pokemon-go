package com.tomer.poke.notifier;

import android.app.ActivityManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import com.android.vending.billing.IInAppBillingService;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity implements ContextConstant, CompoundButton.OnCheckedChangeListener, View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle("");
        setSupportActionBar(toolbar);

        //Set toolbar title + font
        TextView toolbarTV = (TextView) toolbar.findViewById(R.id.toolbar_title);
        toolbarTV.setTypeface(Typeface.createFromAsset(getAssets(), "fonts/pokemon_font.ttf"));
        toolbarTV.setTextColor(getResources().getColor(R.color.colorAccent));

        //Set switch listener
        SwitchCompat masterSwitch = (SwitchCompat) findViewById(R.id.master_switch);
        masterSwitch.setOnCheckedChangeListener(this);

        //Set click listeners
        findViewById(R.id.create_shortcut).setOnClickListener(this);
        findViewById(R.id.support).setOnClickListener(this);

        //Set up IAP
        Intent billingServiceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
        billingServiceIntent.setPackage("com.android.vending");
        bindService(billingServiceIntent, mServiceConn, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //The user donated a dollar
        try {
            //Try to consume the purchase so the user can donate again later
            mService.consumePurchase(3, getPackageName(), new JSONObject(data.getStringExtra("INAPP_PURCHASE_DATA")).getString("purchaseToken"));
        } catch (RemoteException | JSONException | RuntimeException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        //If trying to turn the service on
        if (b) {
            //Check If the app has permission to read system logs
            if (isPermissionGranted(this)) {
                //Update the UI
                ((TextView) findViewById(R.id.status)).setText(getString(R.string.status_active));
                //Start the service
                startService(this);
                //Start Pokemon GO
                startPokemonGO(this);
                return;
            }
            //Prompt and ask to enable the permission
            noPermissionPrompt();
            //Update the UI
            compoundButton.setChecked(false);
            ((TextView) findViewById(R.id.status)).setText(getString(R.string.status_inactive));
            return;
        }
        //Trying to turn the service off - stop the service
        stopService();
        //Update the UI
        ((TextView) findViewById(R.id.status)).setText(getString(R.string.status_inactive));
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.create_shortcut:
                Intent shortcutIntent = new Intent(getApplicationContext(), ShortcutActivity.class);
                shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                Intent addIntent = new Intent();
                addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
                addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.shortcut_label));
                addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(getApplicationContext(), R.mipmap.ic_launcher));
                addIntent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
                getApplicationContext().sendBroadcast(addIntent);
                Toast.makeText(MainActivity.this, "Shortcut created", Toast.LENGTH_SHORT).show();
                break;
            case R.id.support:
                try {
                    Bundle buyIntentBundle = mService.getBuyIntent(3, getPackageName(),
                            SecretConstants.getPropertyValue(getApplicationContext(), "IAPID"), "inapp", SecretConstants.getPropertyValue(getApplicationContext(), "googleIAPCode"));
                    PendingIntent pendingIntent = buyIntentBundle.getParcelable("BUY_INTENT");
                    assert pendingIntent != null;
                    startIntentSenderForResult(pendingIntent.getIntentSender(), 1001, new Intent(), 0, 0, 0);
                } catch (IntentSender.SendIntentException | RemoteException e) {
                    e.printStackTrace();
                }
                break;
        }
    }

    private void noPermissionPrompt() {
        //Show the alert dialog
        new AlertDialog.Builder(MainActivity.this)
                .setTitle(getString(R.string.required_step))
                .setMessage(getString(R.string.required_step_desc))
                .setPositiveButton(getString(R.string.root_workaround), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle(getString(R.string.root_workaround))
                                .setMessage(getString(R.string.root_workaround_desc))
                                .setPositiveButton(R.string.share_command, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        Intent share = new Intent(Intent.ACTION_SEND);
                                        share.setType("text/plain");
                                        share.putExtra(Intent.EXTRA_TEXT, getString(R.string.adb_command));
                                        startActivity(Intent.createChooser(share, getString(R.string.share_command_title)));
                                    }
                                })
                                .setNegativeButton(getString(R.string.how_to), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        openUrl("http://lifehacker.com/the-easiest-way-to-install-androids-adb-and-fastboot-to-1586992378");
                                    }
                                })
                                .setNeutralButton(getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        dialogInterface.dismiss();
                                    }
                                }).show();
                    }
                })
                .setNegativeButton("Root", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        openUrl("http://www.xda-developers.com/root");
                    }
                })
                .setNeutralButton("Close", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                    }
                })
                .show();

    }

    public static boolean isPermissionGranted(Context c) {
        //Check if the app has permission to read system log
        String pname = c.getPackageName();
        String[] CMDLINE_GRANTPERMS = {"su", "-c", null};
        if (c.getPackageManager().checkPermission(android.Manifest.permission.READ_LOGS, pname) != 0) {
            try {
                CMDLINE_GRANTPERMS[2] = String.format("pm grant %s android.permission.READ_LOGS", pname);
                java.lang.Process p = Runtime.getRuntime().exec(CMDLINE_GRANTPERMS);
                int res = p.waitFor();
                Log.d(MAIN_ACTIVITY_LOG_TAG, "exec returned: " + res);
                if (res != 0)
                    throw new Exception("failed to become root");
                else
                    return true;
            } catch (Exception e) {
                Log.d(MAIN_ACTIVITY_LOG_TAG, "exec(): " + e);
                return false;
            }
        } else
            return true;
    }

    public static void startService(Context c) {
        //Start the listener service
        c.startService(new Intent(c.getApplicationContext(), MainService.class));
    }

    private void stopService() {
        //Stop the listener service
        stopService(new Intent(getApplicationContext(), MainService.class));
    }

    private void openUrl(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    public static void killPokemonGO(Context c) {
        ((ActivityManager) c.getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE)).killBackgroundProcesses(POKEMON_GO_PACKAGE_NAME);
    }

    public static void startPokemonGO(Context c) {
        c.startActivity(c.getPackageManager().getLaunchIntentForPackage(POKEMON_GO_PACKAGE_NAME));
    }

    private IInAppBillingService mService;
    ServiceConnection mServiceConn = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = IInAppBillingService.Stub.asInterface(service);
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopService();
    }
}
