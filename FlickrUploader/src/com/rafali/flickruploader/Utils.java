package com.rafali.flickruploader;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences.Editor;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.provider.Settings.Secure;
import android.util.TypedValue;
import android.view.WindowManager;
import com.google.common.base.Joiner;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.googlecode.androidannotations.api.BackgroundExecutor;
import com.rafali.flickruploader.FlickrApi.PRIVACY;
import com.rafali.flickruploader.FlickrUploaderActivity.TAB;
import org.slf4j.LoggerFactory;
import uk.co.senab.bitmapcache.BitmapLruCache;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Utils {

	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(Utils.class);
	private static final float textSize = 16.0f;
	private static BitmapLruCache mCache;

	public static void confirmSignIn(final Activity context) {
		context.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				AlertDialog alertDialog = new AlertDialog.Builder(context).setTitle("Sign into Flickr").setMessage("A Flickr account is required to upload photos.")
						.setPositiveButton("Sign in now", new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								context.startActivityForResult(WebAuth_.intent(context).get(), 14);
							}
						}).setNegativeButton("Later", null).setCancelable(false).show();
				setButtonSize(alertDialog);
			}
		});
	}

	private static void setButtonSize(AlertDialog alert) {
		alert.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize);
		alert.getButton(AlertDialog.BUTTON_NEGATIVE).setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize);
	}

	public static void confirmCancel(final Activity context, final int nb) {
		context.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				AlertDialog alertDialog = new AlertDialog.Builder(context).setTitle("Cancel uploads").setMessage("Do you want to cancel " + nb + " upload" + (nb > 1 ? "s" : ""))
						.setPositiveButton("Cancel uploads", new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								UploadService.cancel(true);
							}
						}).setNegativeButton("Continue", null).setCancelable(false).show();
				setButtonSize(alertDialog);
			}
		});
	}

	private static int screenWidthPx = -1;

	public static int getScreenWidthPx() {
		if (screenWidthPx < 0) {
			Point size = new Point();
			WindowManager wm = (WindowManager) FlickrUploader.getAppContext().getSystemService(Context.WINDOW_SERVICE);
			wm.getDefaultDisplay().getSize(size);
			screenWidthPx = Math.min(size.x, size.y);
		}
		return screenWidthPx;
	}

	public static void dialogPrivacy(final Activity context, final PRIVACY privacy, final Callback<PRIVACY> callback) {
		context.runOnUiThread(new Runnable() {
			@Override
			public void run() {

				final PRIVACY[] privacies = FlickrApi.PRIVACY.values();
				String[] items = new String[privacies.length];
				for (int i = 0; i < FlickrApi.PRIVACY.values().length; i++) {
					items[i] = privacies[i].getSimpleName();
				}
				int checked = -1;
				if (privacy != null) {
					checked = privacy.ordinal();
				}
				final PRIVACY[] result = new PRIVACY[1];
				AlertDialog alertDialog = new AlertDialog.Builder(context).setTitle("Choose privacy").setSingleChoiceItems(items, checked, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						result[0] = privacies[which];
					}
				}).setPositiveButton("OK", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (privacy != result[0])
							callback.onResult(result[0]);
					}
				}).setNegativeButton("Cancel", null).setCancelable(false).show();
				setButtonSize(alertDialog);
			}
		});
	}

	static final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(FlickrUploader.getAppContext());

	public static void setStringProperty(String property, String value) {
		Editor editor = sp.edit();
		editor.putString(property, value);
		editor.apply();
		editor.commit();
	}

	public static String getStringProperty(String property) {
		return sp.getString(property, null);
	}

	public static void clearProperty(String property) {
		Editor editor = sp.edit();
		editor.remove(property);
		editor.apply();
		editor.commit();
	}

	public static void setLongProperty(String property, Long value) {
		Editor editor = sp.edit();
		editor.putLong(property, value);
		editor.apply();
		editor.commit();
	}

	private static String email;

	public static String getEmail() {
		if (email == null) {
			email = getStringProperty(STR.email);
			if (email == null) {
				AccountManager accountManager = AccountManager.get(FlickrUploader.getAppContext());
				final Account[] accounts = accountManager.getAccountsByType("com.google");
				for (Account account : accounts) {
					if (account.name != null) {
						String name = account.name.toLowerCase(Locale.ENGLISH).trim();
						if (account.name.matches(ToolString.REGEX_EMAIL)) {
							email = name;
						}
					}
				}
				if (email == null) {
					email = getDeviceId() + "@fake.com";
				}
				setStringProperty(STR.email, email);
			}
		}
		return email;
	}

	private static String deviceId;

	public static String getDeviceId() {
		if (deviceId == null) {
			deviceId = Secure.getString(FlickrUploader.getAppContext().getContentResolver(), Secure.ANDROID_ID);
			if (deviceId == null) {
				deviceId = getStringProperty("deviceId");
				if (deviceId == null) {
					deviceId = "fake_" + UUID.randomUUID();
					setStringProperty("deviceId", deviceId);
				}
			}
		}
		return deviceId;
	}

	public static long getLongProperty(String property) {
		return sp.getLong(property, 0);
	}

	public static boolean getBooleanProperty(String property, boolean defaultValue) {
		return sp.getBoolean(property, defaultValue);
	}

	public static PRIVACY getDefaultPrivacy() {
		return PRIVACY.valueOf(sp.getString(Preferences.UPLOAD_PRIVACY, PRIVACY.PRIVATE.toString()));
	}

	public static void setBooleanProperty(String property, Boolean value) {
		Editor editor = sp.edit();
		editor.putBoolean(property, value);
		editor.apply();
		editor.commit();
	}

	public static void setImages(String key, Collection<Media> images) {
		try {
			String serialized;
			synchronized (images) {
				if (images == null || images.isEmpty()) {
					serialized = null;
				} else {
					List<Integer> ids = new ArrayList<Integer>();
					for (Media image : images) {
						ids.add(image.id);
					}
					serialized = Joiner.on(",").join(ids);
				}
			}
			LOG.debug("persisting images " + key + " : " + serialized);
			setStringProperty(key, serialized);
		} catch (Throwable e) {
			LOG.error(e.getMessage(), e);

		}
	}

	public static List<Media> getImages(String key) {
		String queueIds = getStringProperty(key);
		if (ToolString.isNotBlank(queueIds)) {
			String filter = Images.Media._ID + " IN (" + queueIds + ")";
			List<Media> images = Utils.loadImages(filter);
			LOG.debug(key + " - queueIds : " + queueIds.split(",").length + ", images:" + images.size());
			return images;
		}
		return null;
	}

	public static List<Media> getImages(Collection<Integer> ids) {
		List<Media> images = null;
		if (ids != null && !ids.isEmpty()) {
			String filter = Images.Media._ID + " IN (" + Joiner.on(",").join(ids) + ")";
			images = Utils.loadImages(filter);
		}
		return images;
	}

	public static Media getImage(int id) {
		String filter = Images.Media._ID + " IN (" + id + ")";
		List<Media> images = Utils.loadImages(filter);
		if (!images.isEmpty()) {
			return images.get(0);
		}
		LOG.warn("id " + id + " not found!");
		return null;
	}

	public static void setMapProperty(String property, Map<String, String> map) {
		Editor editor = sp.edit();
		if (map == null || map.isEmpty()) {
			editor.putString(property, null);
		} else {
			StringBuilder strb = new StringBuilder();
			Iterator<String> it = map.keySet().iterator();
			while (it.hasNext()) {
				String key = it.next();
				String value = map.get(key);
				strb.append(key);
				strb.append("|=|");
				strb.append(value);
				if (it.hasNext())
					strb.append("|;|");
			}
			editor.putString(property, strb.toString());
		}
		editor.apply();
		editor.commit();
	}

	public static void setMapIntegerProperty(String property, Map<Integer, Integer> map) {
		Editor editor = sp.edit();
		if (map == null || map.isEmpty()) {
			editor.putString(property, null);
		} else {
			StringBuilder strb = new StringBuilder();
			Iterator<Integer> it = map.keySet().iterator();
			while (it.hasNext()) {
				Integer key = it.next();
				Integer value = map.get(key);
				strb.append(key);
				strb.append("|=|");
				strb.append(value);
				if (it.hasNext())
					strb.append("|;|");
			}
			editor.putString(property, strb.toString());
		}
		editor.apply();
		editor.commit();
	}

	public static BitmapLruCache getCache() {
		if (mCache == null) {
			BitmapLruCache.Builder builder = new BitmapLruCache.Builder(FlickrUploader.getAppContext());
			builder.setMemoryCacheEnabled(true).setMemoryCacheMaxSizeUsingHeapSize();
			mCache = builder.build();
		}
		return mCache;
	}

	public static Map<String, String> getMapProperty(String property) {
		Map<String, String> map = new LinkedHashMap<String, String>();
		String str = sp.getString(property, null);
		if (str != null) {
			String[] entries = str.split("\\|;\\|");
			for (String entry : entries) {
				String[] split = entry.split("\\|=\\|");
				map.put(split[0], split[1]);
			}
		}
		return map;
	}

	public static Map<Integer, Integer> getMapIntegerProperty(String property) {
		Map<Integer, Integer> map = new LinkedHashMap<Integer, Integer>();
		String str = sp.getString(property, null);
		if (str != null) {
			String[] entries = str.split("\\|;\\|");
			for (String entry : entries) {
				String[] split = entry.split("\\|=\\|");
				map.put(Integer.valueOf(split[0]), Integer.valueOf(split[1]));
			}
		}
		return map;
	}

	private static String SHA1(Media image) {
		return SHA1(image.path + "_" + new File(image.path).length());
	}

	public static String getSHA1tag(Media image) {
		return "file:sha1sig=" + SHA1(image).toLowerCase(Locale.US);
	}

	private static String SHA1(String text) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			byte[] sha1hash = new byte[40];
			md.update(text.getBytes("utf-8"), 0, text.length());
			sha1hash = md.digest();
			return Utils.convertToHex(sha1hash);
		} catch (Exception e) {
			LOG.warn("Error while hashing", e);
		}
		return null;
	}

	static String convertToHex(byte[] data) {
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < data.length; i++) {
			int halfbyte = (data[i] >>> 4) & 0x0F;
			int two_halfs = 0;
			do {
				if ((0 <= halfbyte) && (halfbyte <= 9))
					buf.append((char) ('0' + halfbyte));
				else
					buf.append((char) ('A' + (halfbyte - 10)));
				halfbyte = data[i] & 0x0F;
			} while (two_halfs++ < 1);
		}
		return buf.toString();
	}

	private static byte[] createChecksum(String filename) {
		try {
			InputStream fis = new FileInputStream(filename);

			byte[] buffer = new byte[1024];
			MessageDigest complete;
			complete = MessageDigest.getInstance("MD5");
			int numRead;

			do {
				numRead = fis.read(buffer);
				if (numRead > 0) {
					complete.update(buffer, 0, numRead);
				}
			} while (numRead != -1);

			fis.close();
			return complete.digest();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	static final Map<String, String> md5Sums = new HashMap<String, String>();

	public static final String getMD5Checksum(Media image) {
		String filename = image.path;
		String md5sum = md5Sums.get(filename);
		if (md5sum == null) {
			md5sum = getMD5Checksum(filename);
			md5Sums.put(filename, md5sum);
		}
		return md5sum;
	}

	// see this How-to for a faster way to convert
	// a byte array to a HEX string
	private static String getMD5Checksum(String filename) {
		byte[] b = createChecksum(filename);
		String result = "";

		for (int i = 0; i < b.length; i++) {
			result += Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1);
		}
		return result;
	}

	private static ExecutorService queue = Executors.newSingleThreadExecutor();

	public static ExecutorService getQueue() {
		if (queue == null)
			queue = Executors.newSingleThreadExecutor();
		return queue;
	}

	public static void shutdownQueueNow() {
		if (queue != null) {
			queue.shutdownNow();
			queue = null;
		}
	}

	public static enum MediaType {
		photo, video
	}

	static final String[] projPhoto = { Images.Media._ID, Images.Media.DATA, Images.Media.DATE_ADDED, Images.Media.DATE_TAKEN, Images.Media.DISPLAY_NAME, Images.Media.SIZE };
	static final String[] projVideo = { Video.Media._ID, Video.Media.DATA, Video.Media.DATE_ADDED, Video.Media.DATE_TAKEN, Video.Media.DISPLAY_NAME, Video.Media.SIZE };

	public static List<Media> loadImages(String filter) {
		return loadImages(filter, 0);
	}

	public static List<Media> loadImages(String filter, int limit) {
		List<Media> photos = Utils.loadImages(filter, MediaType.photo, limit);
		List<Media> videos = Utils.loadImages(filter, MediaType.video, limit);
		List<Media> images = new ArrayList<Media>(photos);
		images.addAll(videos);
		Collections.sort(images, MEDIA_COMPARATOR);
		return images;
	}

	public static List<Media> loadImages(String filter, MediaType mediaType) {
		return loadImages(filter, mediaType, 0);
	}

	public static List<Media> loadImages(String filter, MediaType mediaType, int limit) {
		Cursor cursor = null;
		List<Media> images = new ArrayList<Media>();
		try {

			// long oneDayAgo = System.currentTimeMillis() - 24 * 3600 * 1000L;
			// String filter = Images.Media.DATE_TAKEN + " > " + oneDayAgo;
			// String filter = Images.Media._ID + " IN (54820, 56342)";

			String orderBy = Images.Media.DATE_TAKEN + " DESC, " + Images.Media.DATE_ADDED + " DESC";
			if (limit > 0) {
				orderBy += " LIMIT " + limit;
			}
			Uri uri;
			String[] proj = mediaType == MediaType.photo ? projPhoto : projVideo;
			if (filter != null && filter.startsWith("content://")) {
				uri = Uri.parse(filter);
				cursor = FlickrUploader.getAppContext().getContentResolver().query(uri, proj, null, null, orderBy);
			} else {
				uri = mediaType == MediaType.photo ? Images.Media.EXTERNAL_CONTENT_URI : Video.Media.EXTERNAL_CONTENT_URI;
				cursor = FlickrUploader.getAppContext().getContentResolver().query(uri, proj, filter, null, orderBy);
			}
			int idColumn = cursor.getColumnIndex(Images.Media._ID);
			int dataColumn = cursor.getColumnIndex(Images.Media.DATA);
			int displayNameColumn = cursor.getColumnIndex(Images.Media.DISPLAY_NAME);
			int dateTakenColumn = cursor.getColumnIndexOrThrow(Images.Media.DATE_TAKEN);
			int dateAddedColumn = cursor.getColumnIndexOrThrow(Images.Media.DATE_ADDED);
			int sizeColumn = cursor.getColumnIndex(Images.Media.SIZE);
			cursor.moveToFirst();
			LOG.debug("filter = " + filter + ", count = " + cursor.getCount());
			int nbErrorConsecutive = 0;
			while (cursor.isAfterLast() == false) {
				try {
					Long date = null;
					String dateStr = null;
					try {
						dateStr = cursor.getString(dateTakenColumn);
						if (ToolString.isBlank(dateStr)) {
							dateStr = cursor.getString(dateAddedColumn);
							if (ToolString.isNotBlank(dateStr)) {
								if (dateStr.trim().length() <= 10) {
									date = Long.valueOf(dateStr) * 1000L;
								} else {
									date = Long.valueOf(dateStr);
								}
							}
						} else {
							date = Long.valueOf(dateStr);
						}
					} catch (Throwable e) {
						LOG.warn(e.getClass().getSimpleName() + " : " + dateStr);
					}
					String data = cursor.getString(dataColumn);
					if (date == null) {
						File file = new File(data);
						date = file.lastModified();
					}

					Media item = new Media();
					item.id = cursor.getInt(idColumn);
					item.mediaType = mediaType;
					item.path = data;
					item.name = cursor.getString(displayNameColumn);
					item.size = cursor.getInt(sizeColumn);
					item.date = date;
					images.add(item);
					cursor.moveToNext();
					nbErrorConsecutive = 0;
				} catch (Throwable e) {
					nbErrorConsecutive++;
					LOG.error(e.getMessage() + ", nbErrorConsecutive=" + nbErrorConsecutive, e);
					if (nbErrorConsecutive > 5) {
						break;
					}
				}
			}
		} catch (Throwable e) {
			LOG.error(e.getMessage(), e);
		} finally {
			if (cursor != null)
				cursor.close();
		}
		return images;
	}

	public static List<Folder> getFolders(List<Media> images) {
		final Multimap<String, Media> photoFiles = LinkedHashMultimap.create();
		for (Media image : images) {
			int lastIndexOf = image.path.lastIndexOf("/");
			if (lastIndexOf > 0) {
				photoFiles.put(image.path.substring(0, lastIndexOf), image);
			}
		}
		List<Folder> folders = new ArrayList<Folder>();
		for (String path : photoFiles.keySet()) {
			folders.add(new Folder(path, photoFiles.get(path)));
		}
		return folders;
	}

	public enum CAN_UPLOAD {
		ok, network, wifi, charging, manually
	}

	public static CAN_UPLOAD canUploadNow() {
		if (System.currentTimeMillis() < Utils.getLongProperty(STR.manuallyPaused)) {
			return CAN_UPLOAD.manually;
		}
		if (Utils.getBooleanProperty(Preferences.CHARGING_ONLY, false)) {
			if (!checkIfCharging()) {
				return CAN_UPLOAD.charging;
			}
		}

		ConnectivityManager manager = (ConnectivityManager) FlickrUploader.getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetwork = manager.getActiveNetworkInfo();

		if (activeNetwork == null || !activeNetwork.isConnected()) {
			return CAN_UPLOAD.network;
		}

		// if wifi is disabled and the user preference only allows wifi abort
		if (sp.getString(Preferences.UPLOAD_NETWORK, "").equals(STR.wifionly)) {
			switch (activeNetwork.getType()) {
			case ConnectivityManager.TYPE_MOBILE:
			case ConnectivityManager.TYPE_MOBILE_DUN:
			case ConnectivityManager.TYPE_MOBILE_HIPRI:
			case ConnectivityManager.TYPE_MOBILE_MMS:
			case ConnectivityManager.TYPE_MOBILE_SUPL:
				return CAN_UPLOAD.wifi;
			default:
				break;
			}
		}

		return CAN_UPLOAD.ok;
	}

	public static interface Callback<E> {
		public void onResult(E result);
	}

	public static <T extends Enum<T>> Map<String, T> getMapProperty(String key, Class<T> class1) {
		Map<String, String> map = getMapProperty(key);
		Map<String, T> mapE = new HashMap<String, T>();
		try {
			for (Entry<String, String> entry : map.entrySet()) {
				mapE.put(entry.getKey(), Enum.valueOf(class1, entry.getValue()));
			}
		} catch (Throwable e) {
			LOG.warn(e.getMessage(), e);
		}
		return mapE;
	}

	public static Bitmap getBitmap(Media image, TAB tab) {
		Bitmap bitmap = null;
		int retry = 0;
		while (bitmap == null && retry < 3) {
			try {
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inSampleSize = 1;
				options.inPurgeable = true;
				options.inInputShareable = true;
				if (image.mediaType == MediaType.video) {
					// bitmap = ThumbnailUtils.createVideoThumbnail(image.path, Images.Thumbnails.MINI_KIND);
					bitmap = Video.Thumbnails.getThumbnail(FlickrUploader.getAppContext().getContentResolver(), image.id, Video.Thumbnails.MINI_KIND, null);
					return bitmap;
				} else if (tab == TAB.photo) {
					bitmap = Images.Thumbnails.getThumbnail(FlickrUploader.getAppContext().getContentResolver(), image.id, Images.Thumbnails.MICRO_KIND, options);
				} else if (tab == TAB.folder) {
					bitmap = Images.Thumbnails.getThumbnail(FlickrUploader.getAppContext().getContentResolver(), image.id, Images.Thumbnails.MINI_KIND, options);
				} else {
					// First decode with inJustDecodeBounds=true to check dimensions
					final BitmapFactory.Options opts = new BitmapFactory.Options();
					opts.inJustDecodeBounds = true;
					opts.inPurgeable = true;
					opts.inInputShareable = true;
					BitmapFactory.decodeFile(image.path, opts);
					// BitmapFactory.decodeFileDescriptor(file., null, opts);

					// Calculate inSampleSize
					opts.inJustDecodeBounds = false;
					opts.inSampleSize = calculateInSampleSize(opts, getScreenWidthPx(), getScreenWidthPx()) + retry;
					bitmap = BitmapFactory.decodeFile(image.path, opts);
				}
			} catch (OutOfMemoryError e) {
				LOG.warn("retry : " + retry + ", " + e.getMessage(), e);
			} catch (Throwable e) {
				LOG.error(e.getMessage(), e);
			} finally {
				retry++;
			}
		}
		return bitmap;
	}

	static Set<String> syncedFolder;

	static boolean isAutoUpload(Folder folder) {
		if (!Utils.getBooleanProperty(Preferences.AUTOUPLOAD, true) && !Utils.getBooleanProperty(Preferences.AUTOUPLOAD_VIDEOS, true)) {
			return false;
		}
		ensureSyncedFolder();
		return syncedFolder.contains(folder.path);
	}

	static void setAutoUploaded(Folder folder, boolean synced) {
		ensureSyncedFolder();
		if (synced) {
			syncedFolder.add(folder.path);
		} else {
			syncedFolder.remove(folder.path);
		}
		setStringList("syncedFolder", syncedFolder);
	}

	private static void ensureSyncedFolder() {
		if (syncedFolder == null) {
			List<String> persisted = getStringList("syncedFolder", true);
			if (persisted == null) {
				persisted = new ArrayList<String>();
				try {
					addFolder(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), persisted);
					addFolder(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), persisted);
					addFolder(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), persisted);
					LOG.debug("default synced folders : " + persisted);
					setStringList("syncedFolder", persisted);
				} catch (Throwable e) {
					LOG.error(e.getMessage(), e);
				}
			}
			syncedFolder = new HashSet<String>(persisted);
		}
	}

	public static List<String> getAutoUploadFoldersName() {
		ensureSyncedFolder();
		List<String> names = new ArrayList<String>();
		for (String folderPath : syncedFolder) {
			names.add(new File(folderPath).getName());
		}
		return names;
	}

	static void addFolder(File folder, List<String> persisted) {
		if (folder != null) {
			File[] listFiles = folder.listFiles();
			if (listFiles != null) {
				for (File file : listFiles) {
					if (file.isDirectory() && !file.isHidden()) {
						persisted.add(file.getAbsolutePath());
					}
				}
			}
			persisted.add(folder.getAbsolutePath());
		}
	}

	public static List<String> getStringList(String key) {
		return getStringList(key, false);
	}

	public static List<String> getStringList(String key, boolean returnNull) {
		String photosSeen = sp.getString(key, null);
		if (photosSeen != null) {
			return Arrays.asList(photosSeen.split("\\|"));
		} else if (returnNull) {
			return null;
		}
		return new ArrayList<String>();
	}

	public static void setStringList(String key, Collection<String> ids) {
		setStringProperty(key, Joiner.on('|').join(ids));
	}

	/**
	 * Calculate an inSampleSize for use in a {@link BitmapFactory.Options} object when decoding bitmaps using the decode* methods from {@link BitmapFactory}. This implementation calculates the
	 * closest inSampleSize that will result in the final decoded bitmap having a width and height equal to or larger than the requested width and height. This implementation does not ensure a power
	 * of 2 is returned for inSampleSize which can be faster when decoding but results in a larger bitmap which isn't as useful for caching purposes.
	 * 
	 * @param options
	 *            An options object with out* params already populated (run through a decode* method with inJustDecodeBounds==true
	 * @param reqWidth
	 *            The requested width of the resulting bitmap
	 * @param reqHeight
	 *            The requested height of the resulting bitmap
	 * @return The value to be used for inSampleSize
	 */
	private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
		// Raw height and width of image
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;

		if (height > reqHeight || width > reqWidth) {
			if (width > height) {
				inSampleSize = Math.round((float) height / (float) reqHeight);
			} else {
				inSampleSize = Math.round((float) width / (float) reqWidth);
			}

			// This offers some additional logic in case the image has a strange
			// aspect ratio. For example, a panorama may have a much larger
			// width than height. In these cases the total pixels might still
			// end up being too large to fit comfortably in memory, so we should
			// be more aggressive with sample down the image (=larger
			// inSampleSize).

			final double totalPixels = width * height;

			// Anything more than 2x the requested pixels we'll sample down
			// further.
			final double totalReqPixelsCap = reqWidth * reqHeight * 2;

			while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap && inSampleSize < 24) {
				inSampleSize++;
			}
		}
		return inSampleSize;
		// int scale = 1;
		// if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
		// scale = (int) Math.pow(2, (int) Math.round(Math.log(options.outHeight / (double) Math.max(options.outHeight, options.outWidth)) / Math.log(0.5)));
		// }
		// return scale;
	}

	public static <T extends Enum<T>> void setEnumMapProperty(String property, Map<String, T> mapE) {
		Map<String, String> map = new HashMap<String, String>();
		for (Entry<String, T> entry : mapE.entrySet()) {
			map.put(entry.getKey(), entry.getValue().toString());
		}
		setMapProperty(property, map);
	}

	public static String getString(int stringId, Object... objects) {
		return FlickrUploader.getAppContext().getResources().getString(stringId, objects);
	}

	public static String getInstantAlbumId() {
		String instantCustomAlbumId = getStringProperty(STR.instantCustomAlbumId);
		if (instantCustomAlbumId != null) {
			return instantCustomAlbumId;
		} else {
			return getStringProperty(STR.instantAlbumId);
		}
	}

	public static String getInstantAlbumTitle() {
		String instantCustomAlbumId = getStringProperty(STR.instantCustomAlbumId);
		if (instantCustomAlbumId != null) {
			return getStringProperty(STR.instantCustomAlbumTitle);
		} else {
			return STR.instantUpload;
		}
	}

	public static final Comparator<Media> MEDIA_COMPARATOR = new Comparator<Media>() {
		@Override
		public int compare(Media arg0, Media arg1) {
			if (arg0.date > arg1.date) {
				return -1;
			} else if (arg0.date < arg1.date) {
				return 1;
			} else {
				return 0;
			}
		}
	};

	private static boolean charging = false;


	public static void setCharging(boolean charging) {
		Utils.charging = charging;
	}

	public static boolean checkIfCharging() {
		try {
			IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
			Intent batteryStatus = FlickrUploader.getAppContext().registerReceiver(null, ifilter);
			int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
			boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
			setCharging(isCharging);
		} catch (Throwable e) {
			LOG.error(e.getMessage(), e);
		}
		return charging;
	}

	private static boolean copyToFile(InputStream inputStream, File destFile) {
		try {
			OutputStream out = new FileOutputStream(destFile);
			try {
				byte[] buffer = new byte[4096];
				int bytesRead;
				while ((bytesRead = inputStream.read(buffer)) >= 0) {
					out.write(buffer, 0, bytesRead);
				}
			} finally {
				out.close();
			}
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	// copy a file from srcFile to destFile, return true if succeed, return
	// false if fail
	public static boolean copyFile(File srcFile, File destFile) {
		boolean result = false;
		try {
			InputStream in = new FileInputStream(srcFile);
			try {
				result = copyToFile(in, destFile);
			} finally {
				in.close();
			}
		} catch (IOException e) {
			result = false;
		}
		return result;
	}

	static boolean showingEmailActivity = false;

	public static void showEmailActivity(final Activity activity, final String subject, final String message, final boolean attachLogs) {
		if (!showingEmailActivity) {
			showingEmailActivity = true;
			BackgroundExecutor.execute(new Runnable() {

				@Override
				public void run() {
					try {
						Intent intent = new Intent(Intent.ACTION_SEND);
						intent.setType("text/email");
						intent.putExtra(Intent.EXTRA_EMAIL, new String[] { "flickruploader@rafali.com" });
						intent.putExtra(Intent.EXTRA_SUBJECT, subject);
						intent.putExtra(Intent.EXTRA_TEXT, message);

						if (attachLogs) {
							File log = new File(FlickrUploader.getLogFilePath());
							if (log.exists()) {
								File publicDownloadDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
								File publicLog = new File(publicDownloadDirectory, "flickruploader_log.txt");
								Utils.copyFile(log, publicLog);
								try {
									BufferedWriter bW = new BufferedWriter(new FileWriter(publicLog, true));
									bW.newLine();
									bW.write("app version : " + Config.FULL_VERSION_NAME);
									bW.newLine();
									bW.write("device id : " + getDeviceId());
									bW.newLine();
									bW.write("date install : " + FlickrUploader.getAppContext().getPackageManager().getPackageInfo(FlickrUploader.getAppContext().getPackageName(), 0).firstInstallTime);
									bW.newLine();
									bW.flush();
									bW.close();
								} catch (Throwable e) {
									LOG.error(e.getMessage(), e);
								}
								Uri uri = Uri.fromFile(publicLog);
								intent.putExtra(Intent.EXTRA_STREAM, uri);
							} else {
								LOG.warn(log + " does not exist");
							}
						}
						final List<ResolveInfo> resInfoList = activity.getPackageManager().queryIntentActivities(intent, 0);

						ResolveInfo gmailResolveInfo = null;
						for (ResolveInfo resolveInfo : resInfoList) {
							if ("com.google.android.gm".equals(resolveInfo.activityInfo.packageName)) {
								gmailResolveInfo = resolveInfo;
								break;
							}
						}

						if (gmailResolveInfo != null) {
							intent.setClassName(gmailResolveInfo.activityInfo.packageName, gmailResolveInfo.activityInfo.name);
							activity.startActivity(intent);
						} else {
							activity.startActivity(Intent.createChooser(intent, "Send Feedback:"));
						}
					} catch (Throwable e) {
						LOG.error(e.getMessage(), e);
					} finally {
						showingEmailActivity = false;
					}
				}
			});
		}
	}
	
	public static String getUploadDescription() {
		return sp.getString("upload_description", "");
	}
}
