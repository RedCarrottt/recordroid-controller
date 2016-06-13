package com.android.server.recordroid;

public class PreferenceConstants {
	public static final String KEY_MAIN_FRAME_WINDOW_X = "MainFrame_Window_X";
	public static final String KEY_MAIN_FRAME_WINDOW_Y = "MainFrame_Window_Y";
	public static final int INIT_MAIN_FRAME_WINDOW_X = 200;
	public static final int INIT_MAIN_FRAME_WINDOW_Y = 200;

	public static final String KEY_RECORD_FILECHOOSER_PATH = "Record_FileChooserPath";
	public static final String KEY_RESPONSE_FILECHOOSER_PATH = "Response_FileChooserPath";
	public static final String INIT_RECORD_FILECHOOSER_PATH = System
			.getProperty("user.dir");
	public static final String INIT_RESPONSE_FILECHOOSER_PATH = System
			.getProperty("user.dir");

	public static final String KEY_HISTORY_TEXT_LIMIT = "HistoryText_Limit";
	public static final int INIT_HISTORY_TEXT_LIMIT = 10000;

	public static final String KEY_MINIMUM_PRELOAD_INTERVAL_US = "Minimum_Preload_Interval_US";
	public static final String KEY_MINIMUM_PRELOAD_SIZE = "Minimum_Preload_Size";
	public static final long INIT_MINIMUM_PRELOAD_INTERVAL_US = 5 * 1000 * 1000;
	public static final int INIT_MINIMUM_PRELOAD_SIZE = 5000;
	
	public static final String KEY_MAXIMUM_SLEEP_MS = "Maximum_Sleep_MS";
	public static final int INIT_MAXIMUM_SLEEP_MS = 0;
}
