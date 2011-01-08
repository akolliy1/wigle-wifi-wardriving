package net.wigle.wigleandroid;

import java.util.concurrent.atomic.AtomicBoolean;

import org.andnav.osm.util.GeoPoint;
import org.andnav.osm.views.OpenStreetMapViewController;
import org.andnav.osm.views.overlay.MyLocationOverlay;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.location.Location;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

/**
 * show a map!
 */
public final class MappingActivity extends Activity {
  private static class State {
    private boolean locked = true;
    private boolean firstMove = true;
    private GeoPoint oldCenter = null;
    private int oldZoom = Integer.MIN_VALUE;
  }
  private final State state = new State();
  
  private OpenStreetMapViewController mapControl;
  private OpenStreetMapViewWrapper mapView;
  private Handler timer;
  private AtomicBoolean finishing;
  private Location previousLocation;
  private int previousRunNets;
  private MyLocationOverlay myLocationOverlay = null;
  
  private static final GeoPoint DEFAULT_POINT = new GeoPoint( 41950000, -87650000 );
  private static final int MENU_EXIT = 12;
  private static final int MENU_ZOOM_IN = 13;
  private static final int MENU_ZOOM_OUT = 14;
  private static final int MENU_TOGGLE_LOCK = 15;
  private static final int MENU_TOGGLE_NEWDB = 16;
  
  /** Called when the activity is first created. */
  @Override
  public void onCreate( final Bundle savedInstanceState ) {
    super.onCreate( savedInstanceState );
    setContentView( R.layout.map );
    finishing = new AtomicBoolean( false );
    
    // media volume
    this.setVolumeControlStream( AudioManager.STREAM_MUSIC );  
    
    final Object stored = getLastNonConfigurationInstance();
    GeoPoint oldCenter = null;
    int oldZoom = Integer.MIN_VALUE;
    if ( stored != null && stored instanceof State ) {
      // pry an orientation change, which calls destroy, but we set this in onRetainNonConfigurationInstance
      final State retained = (State) stored;
      state.locked = retained.locked;
      state.firstMove = retained.firstMove;
      oldCenter = retained.oldCenter;
      oldZoom = retained.oldZoom;
    }
    
    setupMapView( oldCenter, oldZoom );
    setupTimer();
  }
  
  @Override
  public Object onRetainNonConfigurationInstance() {
    ListActivity.info( "MappingActivity: onRetainNonConfigurationInstance" );
    // save the map info
    state.oldCenter = mapView.getMapCenter();
    state.oldZoom = mapView.getZoomLevel();
    // return state class to copy data from
    return state;
  }
  
  private void setupMapView( final GeoPoint oldCenter, final int oldZoom ) {
    // view
    mapView = (OpenStreetMapViewWrapper) this.findViewById( R.id.mapview );
    mapView.setBuiltInZoomControls( true );
    mapView.setMultiTouchControls( true );
    
    // my location overlay
    myLocationOverlay = new MyLocationOverlay(this.getApplicationContext(), mapView);
    myLocationOverlay.setLocationUpdateMinTime( ListActivity.LOCATION_UPDATE_INTERVAL );
    myLocationOverlay.setDrawAccuracyEnabled( false );
    mapView.getOverlays().add(myLocationOverlay);
    
    // controller
    mapControl = new OpenStreetMapViewController( mapView );
    GeoPoint centerPoint = DEFAULT_POINT;
    final Location location = ListActivity.lameStatic.location;
    if ( oldCenter != null ) {
      centerPoint = oldCenter;
    }
    else if ( location != null ) {
      centerPoint = new GeoPoint( location );
    }
    else if ( previousLocation != null ) {
      centerPoint = new GeoPoint( previousLocation );
    }
    else {
      // ok, try the saved prefs
      final SharedPreferences prefs = this.getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
      float lat = prefs.getFloat( ListActivity.PREF_PREV_LAT, Float.MIN_VALUE );
      float lon = prefs.getFloat( ListActivity.PREF_PREV_LON, Float.MIN_VALUE );
      if ( lat != Float.MIN_VALUE && lon != Float.MIN_VALUE ) {
        centerPoint = new GeoPoint( lat, lon );
      }
    }
    int zoom = 16;
    if ( oldZoom >= 0 ) {
      zoom = oldZoom;
    }
    mapControl.setCenter( centerPoint );
    mapControl.setZoom( zoom );
    mapControl.setCenter( centerPoint );
    
    ListActivity.info("done setupMapView");
  }
  
  private void setupTimer() {
    if ( timer == null ) {
      timer = new Handler();
      final Runnable mUpdateTimeTask = new Runnable() {
        public void run() {              
            // make sure the app isn't trying to finish
            if ( ! finishing.get() ) {
              final Location location = ListActivity.lameStatic.location;
              if ( location != null ) {
                if ( state.locked ) {
                  // ListActivity.info( "mapping center location: " + location );
  								final GeoPoint locGeoPoint = new GeoPoint( location );
  								if ( state.firstMove ) {
  								  mapControl.setCenter( locGeoPoint );
  								  state.firstMove = false;
  								}
  								else {
  								  mapControl.animateTo( locGeoPoint );
  								}
                }
                else if ( previousLocation == null || previousLocation.getLatitude() != location.getLatitude() 
                    || previousLocation.getLongitude() != location.getLongitude() 
                    || previousRunNets != ListActivity.lameStatic.runNets) {
                  // location or nets have changed, update the view
                  mapView.postInvalidate();
                }
                // set if location isn't null
                previousLocation = location;
              }
              
              previousRunNets = ListActivity.lameStatic.runNets;
              
              TextView tv = (TextView) findViewById( R.id.stats_run );
              tv.setText( "Run: " + ListActivity.lameStatic.runNets );
              tv = (TextView) findViewById( R.id.stats_new );
              tv.setText( "New: " + ListActivity.lameStatic.newNets );
              tv = (TextView) findViewById( R.id.stats_dbnets );
              tv.setText( "DB: " + ListActivity.lameStatic.dbNets );
              
              final long period = 1000L;
              // info("wifitimer: " + period );
              timer.postDelayed( this, period );
            }
            else {
              ListActivity.info( "finishing mapping timer" );
            }
        }
      };
      timer.removeCallbacks( mUpdateTimeTask );
      timer.postDelayed( mUpdateTimeTask, 100 );
    }
  }
    
  @Override
  public void finish() {
    ListActivity.info( "finish mapping." );
    finishing.set( true );
    
    super.finish();
  }
  
  @Override
  public void onDestroy() {
    ListActivity.info( "destroy mapping." );
    finishing.set( true );
    
    super.onDestroy();
  }
  
  @Override
  public void onPause() {
    ListActivity.info( "pause mapping." );
    myLocationOverlay.disableMyLocation();
    myLocationOverlay.disableCompass();
    
    super.onPause();
  }
  
  @Override
  public void onResume() {
    ListActivity.info( "resume mapping." );
    myLocationOverlay.enableCompass();    
    myLocationOverlay.enableMyLocation();
    
    super.onResume();
  }
  
  /* Creates the menu items */
  @Override
  public boolean onCreateOptionsMenu( final Menu menu ) {
      MenuItem item = menu.add(0, MENU_ZOOM_IN, 0, "Zoom in");
      item.setIcon( android.R.drawable.ic_menu_add );
      
      item = menu.add(0, MENU_ZOOM_OUT, 0, "Zoom out");
      item.setIcon( android.R.drawable.ic_menu_revert );
      
      item = menu.add(0, MENU_EXIT, 0, "Exit");
      item.setIcon( android.R.drawable.ic_menu_close_clear_cancel );
      
      String name = state.locked ? "Turn Off Lockon" : "Turn On Lockon";
      item = menu.add(0, MENU_TOGGLE_LOCK, 0, name);
      item.setIcon( android.R.drawable.ic_menu_mapmode );
      
      final SharedPreferences prefs = this.getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
      final boolean showNewDBOnly = prefs.getBoolean( ListActivity.PREF_MAP_ONLY_NEWDB, false );
      String nameDB = showNewDBOnly ? "Show Run&New" : "Show New Only";
      item = menu.add(0, MENU_TOGGLE_NEWDB, 0, nameDB);
      item.setIcon( android.R.drawable.ic_menu_edit );
      
      
      return true;
  }

  /* Handles item selections */
  @Override
  public boolean onOptionsItemSelected( final MenuItem item ) {
      switch ( item.getItemId() ) {
        case MENU_EXIT: {
          MainActivity.finishListActivity( this );
          finish();
          return true;
        }
        case MENU_ZOOM_IN: {
          int zoom = mapView.getZoomLevel();
          zoom++;
          mapControl.setZoom( zoom );
          return true;
        }
        case MENU_ZOOM_OUT: {
          int zoom = mapView.getZoomLevel();
          zoom--;
          mapControl.setZoom( zoom );
          return true;
        }
        case MENU_TOGGLE_LOCK: {
          state.locked = ! state.locked;
          String name = state.locked ? "Turn Off Lock-on" : "Turn On Lock-on";
          item.setTitle( name );
          return true;
        }
        case MENU_TOGGLE_NEWDB: {
          final SharedPreferences prefs = this.getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
          final boolean showNewDBOnly = ! prefs.getBoolean( ListActivity.PREF_MAP_ONLY_NEWDB, false );
          Editor edit = prefs.edit();
          edit.putBoolean( ListActivity.PREF_MAP_ONLY_NEWDB, showNewDBOnly );
          edit.commit();
          
          String name = showNewDBOnly ? "Show Run&New" : "Show New Only";
          item.setTitle( name );
          return true;
        }
      }
      return false;
  }
  
  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      ListActivity.info( "onKeyDown: not quitting app on back" );
      MainActivity.switchTab( this, MainActivity.TAB_LIST );
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }
  
}
