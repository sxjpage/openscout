// Copyright 2020 Carnegie Mellon University
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package edu.cmu.cs.openscout;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatActivity;

import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ListView;
import android.widget.ImageView;
import androidx.appcompat.widget.Toolbar;
import android.os.Bundle;
import android.Manifest;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import android.util.Log;
import android.content.Context;
import android.hardware.camera2.CameraManager;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import edu.cmu.cs.gabriel.Const;
import edu.cmu.cs.gabriel.GabrielClientActivity;
import edu.cmu.cs.gabriel.client.socket.SocketWrapper;



public class ServerListActivity extends AppCompatActivity implements LocationListener {
    ListView listView;
    EditText serverName;
    EditText serverAddress;
    ImageView add;
    ArrayList<Server> ItemModelList;
    ServerListAdapter serverListAdapter;
    CameraManager camMan = null;
    private SharedPreferences mSharedPreferences;
    private static final int MY_PERMISSIONS_REQUEST_CAMERA = 23;

    void loadPref(SharedPreferences sharedPreferences, String key) {
        Const.loadPref(sharedPreferences, key);
    }

    ServerListAdapter createServerListAdapter() {
        return new ServerListAdapter(this, ItemModelList);
    }

    //activity menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch (id) {
            case R.id.about:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);

                builder.setMessage(this.getString(R.string.about_message, Const.UUID, BuildConfig.VERSION_NAME))
                        .setTitle(R.string.about_title);
                AlertDialog dialog = builder.create();
                dialog.show();
                return true;
            case R.id.settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                this.startActivity(intent);
                return true;
            default:
                return false;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // This verification should be done during onStart() because the system calls
        // this method when the user returns to the activity, which ensures the desired
        // location provider is enabled each time the activity resumes from the stopped state.
        LocationManager locationManager =
                (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        final boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, Const.GPS_UPDATE_TIME, Const.GPS_UPDATE_DIST, this);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, Const.GPS_UPDATE_TIME, Const.GPS_UPDATE_DIST, this);

        if (!gpsEnabled) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_HOLO_DARK);
            builder.setMessage(R.string.enable_gps_text)
                    .setTitle(R.string.enable_gps_title)
                    .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {

                                }
                            }
                    )
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    enableLocationSettings();
                                }
                            }
                    )
                    .setCancelable(false);

            AlertDialog dialog = builder.create();
            dialog.show();
        }
    }

    private void enableLocationSettings() {
        Intent settingsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        startActivity(settingsIntent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestPermission();

        setContentView(R.layout.activity_serverlist);
        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);
        listView = (ListView) findViewById(R.id.listServers);
        serverName = (EditText) findViewById(R.id.addServerName);
        serverAddress = (EditText) findViewById(R.id.addServerAddress);
        add = (ImageView) findViewById(R.id.imgViewAdd);
        ItemModelList = new ArrayList<Server>();
        serverListAdapter = createServerListAdapter();
        listView.setAdapter(serverListAdapter);
        mSharedPreferences=PreferenceManager.getDefaultSharedPreferences(this);
        Map<String, ?> m = mSharedPreferences.getAll();
        for(Map.Entry<String,?> entry : m.entrySet()){
            Log.d("SharedPreferences",entry.getKey() + ": " +
                    entry.getValue().toString());
            this.loadPref(mSharedPreferences, entry.getKey());

        }
        camMan = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        initServerList();
    }

    void requestPermissionHelper(String permissions[]) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            ActivityCompat.requestPermissions(this,
                    permissions,
                    MY_PERMISSIONS_REQUEST_CAMERA);
        }
    }

    void requestPermission() {
        String permissions[] = {Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_FINE_LOCATION
        };
        this.requestPermissionHelper(permissions);
    }


    void initServerList() {
        Map<String, ?> prefs = mSharedPreferences.getAll();
        boolean uuid_set = false;
        for (Map.Entry<String,?> pref : prefs.entrySet())
            if(pref.getKey().startsWith("server:")) {
                Server s = new Server(pref.getKey().substring("server:".length()), pref.getValue().toString());
                ItemModelList.add(s);
                serverListAdapter.notifyDataSetChanged();
            } else if(pref.getKey().startsWith("uuid"))
            {
                Const.UUID = pref.getValue().toString();
                uuid_set = true;
            }

        if(!uuid_set) {
            //Generate UUID for device identification
            String uniqueID = UUID.randomUUID().toString();
            Const.UUID = uniqueID;
            SharedPreferences.Editor editor = mSharedPreferences.edit();
            editor.putString("uuid", uniqueID);
            editor.commit();
        }

        if (prefs.isEmpty()) {
            // Add demo server if there are no other servers present
            Server s = new Server(getString(R.string.demo_server), getString(R.string.demo_dns));
            ItemModelList.add(s);
            serverListAdapter.notifyDataSetChanged();
            SharedPreferences.Editor editor = mSharedPreferences.edit();
            editor.putString("server:".concat(getString(R.string.demo_server)),getString(R.string.demo_dns));
            editor.commit();
        }
    }

    public void addValue(View v) {
        add.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        String name = serverName.getText().toString();
        String endpoint = serverAddress.getText().toString();
        if (name.isEmpty() || endpoint.isEmpty()) {
            Toast.makeText(getApplicationContext(), R.string.error_empty ,
                    Toast.LENGTH_SHORT).show();
        } else if (!SocketWrapper.validUri(endpoint, Const.PORT)) {
            Toast.makeText(getApplicationContext(), R.string.error_invalidURI,
                    Toast.LENGTH_SHORT).show();
        }  else if(mSharedPreferences.contains("server:".concat(name))) {
            Toast.makeText(getApplicationContext(), R.string.error_exists,
                Toast.LENGTH_SHORT).show();
        } else {
            Server s = new Server(name, endpoint);
            ItemModelList.add(s);
            serverListAdapter.notifyDataSetChanged();
            serverName.setText("");
            serverAddress.setText("");
            SharedPreferences.Editor editor = mSharedPreferences.edit();
            editor.putString("server:".concat(name),endpoint);
            editor.commit();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.i("ServerListActivity", "Location changed.");
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.i("ServerListActivity", String.format("Location provider %s enabled.", provider));
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.i("ServerListActivity", String.format("Location provider %s disabled.", provider));
    }
}
