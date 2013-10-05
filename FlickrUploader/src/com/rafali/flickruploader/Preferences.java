package com.rafali.flickruploader;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.text.Html;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;
import com.googlecode.androidannotations.api.BackgroundExecutor;
import com.rafali.flickruploader.FlickrApi.PRIVACY;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Preferences extends PreferenceActivity implements OnSharedPreferenceChangeListener {

	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(Preferences.class);
	private static final String AUTOUPLOAD_PHOTOSET = "autoupload_photoset";
	public static final String UPLOAD_NETWORK = "upload_network";
	public static final String UPLOAD_PRIVACY = "upload_privacy";
	public static final String AUTOUPLOAD = "autoupload";
	public static final String AUTOUPLOAD_VIDEOS = "autouploadvideos";
	public static final String CHARGING_ONLY = "charging_only";

	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		handler = new Handler();
		super.onCreate(savedInstanceState);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		sp = PreferenceManager.getDefaultSharedPreferences(this);
		// add preferences from xml
		addPreferencesFromResource(R.xml.preferences);
		sp.registerOnSharedPreferenceChangeListener(this);
		findPreference("login").setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				if (Utils.getStringProperty(STR.userId) != null) {
					new AlertDialog.Builder(Preferences.this).setIcon(android.R.drawable.ic_dialog_alert).setTitle("Sign out").setMessage("Confirm signing out. Uploads will be disabled.")
							.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									Editor editor = sp.edit();
									editor.remove(STR.userId);
									editor.remove(STR.accessToken);
									editor.remove(STR.accessTokenSecret);
									editor.remove(STR.userDateCreated);
									editor.remove(STR.userName);
									editor.remove(STR.uploadedPhotos);
									editor.remove(STR.instantAlbumId);
									editor.remove(STR.instantCustomAlbumId);
									editor.remove(STR.instantCustomAlbumTitle);
									editor.remove(Preferences.AUTOUPLOAD_PHOTOSET);
									editor.apply();
									editor.commit();
									render();
									FlickrApi.reset();
									UploadService.cancel(false);
								}
							}).setNegativeButton("Cancel", null).show();
				} else {
					WebAuth_.intent(Preferences.this).start();
				}
				return false;
			}
		});
		ListPreference privacyPreference = (ListPreference) findPreference(UPLOAD_PRIVACY);
		PRIVACY[] privacies = FlickrApi.PRIVACY.values();
		int length = privacies.length;
		String[] entries = new String[length];
		String[] values = new String[length];
		for (int i = 0; i < length; i++) {
			entries[i] = privacies[i].getSimpleName();
			values[i] = privacies[i].toString();
		}
		privacyPreference.setEntries(entries);
		privacyPreference.setEntryValues(values);

		findPreference("notifications").setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				startActivity(new Intent(Preferences.this, PreferencesNotification.class));
				return false;
			}
		});
		findPreference("upload_description").setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				AlertDialog.Builder alert = new AlertDialog.Builder(Preferences.this);
				alert.setTitle("Upload description");
                // Set an EditText view to get user input
                final EditText input = new EditText(Preferences.this);
                input.setText(Utils.getUploadDescription());
                alert.setView(input);

                alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String value = input.getText().toString();
                        LOG.debug("value : " + value);
                        Utils.setStringProperty("upload_description", value);
                        render();
                    }
                });

                alert.setNegativeButton("Cancel", null);

				alert.show();
				return false;
			}
		});

		findPreference(AUTOUPLOAD_PHOTOSET).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if ("custom".equals(newValue)) {
					if (ToolString.isBlank(Utils.getStringProperty(STR.userId))) {
						Toast.makeText(Preferences.this, "You need to login to select your photoset", Toast.LENGTH_LONG).show();
					} else {
						final ProgressDialog dialog = ProgressDialog.show(Preferences.this, "", "Loading photosets", true);
						BackgroundExecutor.execute(new Runnable() {
							@Override
							public void run() {
								final Map<String, String> photosets = FlickrApi.getPhotoSets();
								runOnUiThread(new Runnable() {
									@Override
									public void run() {
										dialog.cancel();
										if (photosets.isEmpty()) {
											Toast.makeText(Preferences.this, "No photoset found", Toast.LENGTH_LONG).show();
										} else {
											AlertDialog.Builder builder = new AlertDialog.Builder(Preferences.this);
											final List<String> photosetTitles = new ArrayList<String>();
											final List<String> photosetIds = new ArrayList<String>();
											for (String photosetId : photosets.keySet()) {
												photosetIds.add(photosetId);
												photosetTitles.add(photosets.get(photosetId));
											}
											String[] photosetTitlesArray = photosetTitles.toArray(new String[photosetTitles.size()]);
											builder.setItems(photosetTitlesArray, new OnClickListener() {
												@Override
												public void onClick(DialogInterface dialog, int which) {
													Utils.setStringProperty(STR.instantCustomAlbumId, photosetIds.get(which));
													Utils.setStringProperty(STR.instantCustomAlbumTitle, photosetTitles.get(which));
													Utils.setStringProperty(AUTOUPLOAD_PHOTOSET, "custom");
													((ListPreference) findPreference(AUTOUPLOAD_PHOTOSET)).setValue("custom");
												}
											});
											builder.show();
										}
									}
								});
							}
						});
					}
					return false;
				} else {
					Utils.clearProperty(STR.instantCustomAlbumId);
					Utils.clearProperty(STR.instantCustomAlbumTitle);
				}
				return true;
			}
		});

		findPreference(UPLOAD_NETWORK).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference arg0, Object arg1) {
				if (STR.wifionly.equals(arg1)) {
					AlertDialog.Builder builder = new AlertDialog.Builder(Preferences.this);
					builder.setTitle("WARNING")
							.setMessage("This feature is not guaranteed. If you really want to make sure this app does not use your data plan, enforce it at the OS level as explained in the FAQ.")
							.setNegativeButton("Later", null).setPositiveButton("See the FAQ", new OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									String url = "https://github.com/rafali/flickr-uploader/wiki/FAQ#how-to-make-sure-it-wont-use-my-precious-data-plan";
									Intent i = new Intent(Intent.ACTION_VIEW);
									i.setData(Uri.parse(url));
									startActivity(i);
								}
							});

					builder.create().show();
				}
				return true;
			}
		});

		render();
	}

	@Override
	protected void onStart() {
		super.onStart();
	}

	@Override
	protected void onStop() {
		super.onStop();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
		render();
	}

	SharedPreferences sp;

	@SuppressWarnings("deprecation")
	void render() {
		{
			List<String> values = Arrays.asList(getResources().getStringArray(R.array.network_values));
			String[] entries = getResources().getStringArray(R.array.network_entries);
			String value = sp.getString(UPLOAD_NETWORK, values.get(0));
			findPreference(UPLOAD_NETWORK).setSummary(entries[values.indexOf(value)]);
		}
		{
			String summary;
			if (Utils.getStringProperty(STR.instantCustomAlbumId) == null) {
				summary = "Default";
			} else {
				summary = Utils.getStringProperty(STR.instantCustomAlbumTitle);
			}
			findPreference(AUTOUPLOAD_PHOTOSET).setSummary(summary);
		}
		
		findPreference("upload_description").setSummary(Html.fromHtml(Utils.getUploadDescription()));


		String privacy = sp.getString(UPLOAD_PRIVACY, PRIVACY.PRIVATE.toString());
		findPreference(UPLOAD_PRIVACY).setSummary(PRIVACY.valueOf(privacy).getSimpleName());
		Preference login = findPreference("login");
		if (Utils.getStringProperty(STR.userId) == null) {
			login.setTitle("Sign into Flickr");
			login.setSummary("Account required for upload");
		} else {
			login.setTitle("Sign out");
			login.setSummary(Utils.getStringProperty(STR.userName) + " is currently logged in");
		}
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
	}

	Handler handler;

	@Override
	protected void onDestroy() {
		sp.unregisterOnSharedPreferenceChangeListener(this);
		super.onDestroy();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			finish();
		}
		return super.onOptionsItemSelected(item);
	}
}
