package com.android.server.recordroid;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;

public class MainFrameController implements ActionListener, USBMessageListener,
		ADBConnectionListener, WindowListener {
	private static final int INIT_WINDOW_WIDTH = 700;
	private static final int INIT_WINDOW_HEIGHT = 350;

	public MainFrameController(String title, int targetPort) {
		this.mPrefs = Preferences.userNodeForPackage(MainFrameController.class);
		this.mRecordFile = new TraceFile(this.mPrefs);
		this.mResponseFile = new ResponseFile();

		this.initFrame(title);
		this.initConnectors(targetPort);
		this.updateUI();
	}

	// ADB & USB connector
	private static final int USB_CONNECTOR_SLEEP_MS = 500;
	private ADBConnector mADBConnector;
	private USBConnector mUSBConnector;

	// Preferences
	private Preferences mPrefs;

	// Record file
	private TraceFile mRecordFile;
	private boolean mShouldCloseRecordFile = false;

	// Response file
	private ResponseFile mResponseFile;
	private boolean mShouldCloseResponseFile = false;

	// States
	private ControllerState mControllerState = new ControllerState(
			ControllerState.Type.INITIAL);
	private RecordroidServiceState mMirroredServiceState = null;
	private long mPrevRunningSN = -1;
	private int mPrevReplayBufferIndex = -1;

	// History
	private ArrayList<String> mHistoryArray = new ArrayList<String>();
	private String mHistoryText = "";
	private Boolean mHistoryTextDirty = false;
	private static final String HISTORY_TEXT_COLOR_COMMAND = "#0000FF";
	private static final String HISTORY_TEXT_COLOR_INFO = "#0B610B";
	private static final String HISTORY_TEXT_COLOR_WARNING = "#FF0000";
	@SuppressWarnings("unused")
	private static final String HISTORY_TEXT_COLOR_DEBUG = "#04B404";

	// Frame & panels
	private String mTitle;
	private JFrame mFrame;
	private JPanel mMainPanel;
	private JPanel mLeftColumnPanel;
	private JPanel mRightColumnPanel;

	private JPanel mStatusBarPanel;
	private JPanel mRecordFileBarPanel;
	private JPanel mButtonBarPanel;
	private JPanel mResponseFileBarPanel;
	private JPanel mOptionBarPanel;
	private JPanel mHistoryTextBarPanel;
	private JPanel mHistoryButtonBarPanel;

	// Status bar panel
	private JLabel mStatusLabel;

	// Button bar panel
	private JButton mRecordButton;
	private JButton mReplayButton;

	// Record file bar panel
	private JButton mSelectRecordFileButton;
	private JLabel mRecordFilePathLabel;
	private static final String INIT_SELECT_RECORD_FILE_BUTTON_TEXT = "Record file";
	private static final String INIT_RECORD_FILE_PATH_LABEL_TEXT = "<empty record file>";
	private static final String RECORD_FILECHOOSER_BUTTON_TEXT = "Select";

	// Response file bar panel
	private JButton mSelectResponseFileButton;
	private JLabel mResponseFilePathLabel;
	private JButton mDisableResponseFileButton;
	private static final String INIT_SELECT_RESPONSE_FILE_BUTTON_TEXT = "Response file";
	private static final String INIT_RESPONSE_FILE_PATH_LABEL_TEXT = "<empty response file>";
	private static final String RESPONSE_FILECHOOSER_BUTTON_TEXT = "Select";
	private static final String INIT_DISABLE_RESPONSE_FILE_BUTTON_TEXT = "X";

	// Option bar panel
	private JButton mOptionButton;
	private static final String INIT_OPTION_BUTTON_TEXT = "Options";

	// History text bar panel
	private JScrollPane mHistoryTextPaneScrollPane;
	private JTextPane mHistoryTextPane;

	// History button bar panel
	private JButton mClearHistoryButton;
	private JButton mSkipWaitingInReplayButton;
	private static final String INIT_CLEAR_HISTORY_BUTTON_TEXT = "Clear History";
	private static final String INIT_SKIP_WAITING_IN_REPLAY_BUTTON_TEXT = "Skip Waiting";

	private void initConnectors(int targetPort) {
		this.mADBConnector = ADBConnector.get(targetPort);
		this.mADBConnector.addListener(this);

		this.mUSBConnector = USBConnector.client(targetPort,
				USB_CONNECTOR_SLEEP_MS);
		this.mUSBConnector.addListener(this);

		this.mADBConnector.start();
	}

	private void initFrame(String title) {
		int windowX = this.mPrefs.getInt(
				PreferenceConstants.KEY_MAIN_FRAME_WINDOW_X,
				PreferenceConstants.INIT_MAIN_FRAME_WINDOW_X);
		int windowY = this.mPrefs.getInt(
				PreferenceConstants.KEY_MAIN_FRAME_WINDOW_Y,
				PreferenceConstants.INIT_MAIN_FRAME_WINDOW_Y);

		this.mTitle = title;
		this.mFrame = new JFrame();
		this.mFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.mFrame.setTitle(this.mTitle);
		this.mFrame.setSize(INIT_WINDOW_WIDTH, INIT_WINDOW_HEIGHT);
		this.mFrame.setResizable(false);
		this.mFrame.setLocation(windowX, windowY);
		this.mFrame.addWindowListener(this);
		this.initMainPanel();
		this.mFrame.setVisible(true);
	}

	private void initMainPanel() {
		this.mMainPanel = new JPanel();
		this.mMainPanel.setLayout(new GridLayout(1, 2));
		this.mFrame.getContentPane().add(this.mMainPanel);

		this.mLeftColumnPanel = new JPanel(new GridBagLayout());
		this.initLeftColumnPanel();
		this.mMainPanel.add(this.mLeftColumnPanel);

		this.mRightColumnPanel = new JPanel(new GridBagLayout());
		this.initRightColumnPanel();
		this.mMainPanel.add(this.mRightColumnPanel);
	}

	private void initLeftColumnPanel() {
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 1;
		c.weighty = 0.10;
		c.fill = GridBagConstraints.BOTH;
		this.mStatusBarPanel = new JPanel(new GridLayout(1, 1));
		this.initStatusBarPanel();
		this.mLeftColumnPanel.add(this.mStatusBarPanel, c);

		c.gridx = 0;
		c.gridy = 1;
		c.weightx = 1;
		c.weighty = 0.35;
		c.fill = GridBagConstraints.BOTH;
		this.mButtonBarPanel = new JPanel(new GridLayout(1, 2));
		this.initButtonBarPanel();
		this.mLeftColumnPanel.add(this.mButtonBarPanel, c);

		c.gridx = 0;
		c.gridy = 2;
		c.weightx = 1;
		c.weighty = 0.15;
		c.fill = GridBagConstraints.BOTH;
		this.mRecordFileBarPanel = new JPanel(new GridBagLayout());
		this.initRecordFileBarPanel();
		this.mLeftColumnPanel.add(this.mRecordFileBarPanel, c);

		c.gridx = 0;
		c.gridy = 3;
		c.weightx = 1;
		c.weighty = 0.15;
		c.fill = GridBagConstraints.BOTH;
		this.mResponseFileBarPanel = new JPanel(new GridBagLayout());
		this.initResponseFileBarPanel();
		this.mLeftColumnPanel.add(this.mResponseFileBarPanel, c);

		c.gridx = 0;
		c.gridy = 4;
		c.weightx = 1;
		c.weighty = 0.15;
		c.fill = GridBagConstraints.BOTH;
		this.mOptionBarPanel = new JPanel(new GridBagLayout());
		this.initOptionBarPanel();
		this.mLeftColumnPanel.add(this.mOptionBarPanel, c);
	}

	private void initRightColumnPanel() {
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 1;
		c.weighty = 0.95;
		c.fill = GridBagConstraints.BOTH;
		this.mHistoryTextBarPanel = new JPanel(new GridLayout(1, 1));
		this.initHistoryTextBarPanel();
		this.mRightColumnPanel.add(this.mHistoryTextBarPanel, c);

		c.gridx = 0;
		c.gridy = 1;
		c.weightx = 1;
		c.weighty = 0.05;
		c.fill = GridBagConstraints.BOTH;
		this.mHistoryButtonBarPanel = new JPanel(new GridLayout(1, 1));
		this.initHistoryButtonBarPanel();
		this.mRightColumnPanel.add(this.mHistoryButtonBarPanel, c);
	}

	// Status bar panel
	private void initStatusBarPanel() {
		this.mStatusLabel = new JLabel();
		this.mStatusLabel.setFont(new Font(this.mStatusLabel.getFont()
				.getName(), Font.BOLD, 24));
		this.mStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
		this.mStatusBarPanel.add(this.mStatusLabel);
	}

	// Button bar panel
	private void initButtonBarPanel() {
		this.mRecordButton = new JButton();
		this.mRecordButton.addActionListener(this);
		this.mButtonBarPanel.add(this.mRecordButton);

		this.mReplayButton = new JButton();
		this.mReplayButton.addActionListener(this);
		this.mButtonBarPanel.add(this.mReplayButton);
	}

	// Record file bar panel
	private void initRecordFileBarPanel() {
		GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.WEST;
		c.gridwidth = 100;
		c.weightx = 0.15;
		c.weighty = 1;
		c.fill = GridBagConstraints.BOTH;
		this.mSelectRecordFileButton = new JButton(
				INIT_SELECT_RECORD_FILE_BUTTON_TEXT);
		this.mSelectRecordFileButton.addActionListener(this);
		Dimension minSize = this.mSelectRecordFileButton.getMinimumSize();
		minSize.width = 150;
		this.mSelectRecordFileButton.setMinimumSize(minSize);
		this.mRecordFileBarPanel.add(this.mSelectRecordFileButton, c);

		c.anchor = GridBagConstraints.EAST;
		c.weightx = 0.85;
		c.weighty = 1;
		c.fill = GridBagConstraints.BOTH;
		this.mRecordFilePathLabel = new JLabel();
		this.mRecordFileBarPanel.add(this.mRecordFilePathLabel, c);
	}

	// Response file bar panel
	private void initResponseFileBarPanel() {
		GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.WEST;
		c.gridwidth = 100;
		c.weightx = 0.15;
		c.weighty = 1;
		c.fill = GridBagConstraints.BOTH;
		this.mSelectResponseFileButton = new JButton(
				INIT_SELECT_RESPONSE_FILE_BUTTON_TEXT);
		this.mSelectResponseFileButton.addActionListener(this);
		Dimension minSize = this.mSelectResponseFileButton.getMinimumSize();
		minSize.width = 150;
		this.mSelectResponseFileButton.setMinimumSize(minSize);
		this.mResponseFileBarPanel.add(this.mSelectResponseFileButton, c);

		c.anchor = GridBagConstraints.EAST;
		c.weightx = 0.80;
		c.weighty = 1;
		c.fill = GridBagConstraints.BOTH;
		this.mResponseFilePathLabel = new JLabel();
		this.mResponseFileBarPanel.add(this.mResponseFilePathLabel, c);

		c.anchor = GridBagConstraints.EAST;
		c.weightx = 0.05;
		c.weighty = 1;
		c.fill = GridBagConstraints.BOTH;
		this.mDisableResponseFileButton = new JButton(
				INIT_DISABLE_RESPONSE_FILE_BUTTON_TEXT);
		this.mDisableResponseFileButton.addActionListener(this);
		minSize = this.mDisableResponseFileButton.getMinimumSize();
		minSize.width = 30;
		this.mDisableResponseFileButton.setMinimumSize(minSize);
		this.mResponseFileBarPanel.add(this.mDisableResponseFileButton, c);
	}

	// Option bar panel
	private void initOptionBarPanel() {
		GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.WEST;
		c.gridwidth = 100;
		c.weightx = 1;
		c.weighty = 1;
		c.fill = GridBagConstraints.BOTH;
		this.mOptionButton = new JButton(INIT_OPTION_BUTTON_TEXT);
		this.mOptionButton.addActionListener(this);
		this.mOptionBarPanel.add(this.mOptionButton, c);
	}

	// History text bar panel
	private void initHistoryTextBarPanel() {
		GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.EAST;
		c.gridwidth = 100;
		c.weightx = 1;
		c.weighty = 1;
		c.fill = GridBagConstraints.BOTH;
		this.mHistoryTextPane = new JTextPane();
		this.mHistoryTextPane.setEditable(false);
		this.mHistoryTextPane.setContentType("text/html");
		this.mHistoryTextPaneScrollPane = new JScrollPane(this.mHistoryTextPane);
		this.mHistoryTextPaneScrollPane
				.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		this.mHistoryTextBarPanel.add(this.mHistoryTextPaneScrollPane, c);
	}

	// History button bar panel
	private void initHistoryButtonBarPanel() {
		GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.EAST;
		c.gridwidth = 100;
		c.weightx = 0.6;
		c.weighty = 1;
		c.fill = GridBagConstraints.BOTH;
		this.mClearHistoryButton = new JButton(INIT_CLEAR_HISTORY_BUTTON_TEXT);
		this.mClearHistoryButton.addActionListener(this);
		this.mHistoryButtonBarPanel.add(this.mClearHistoryButton, c);

		c.anchor = GridBagConstraints.EAST;
		c.gridwidth = 100;
		c.weightx = 0.4;
		c.weighty = 1;
		c.fill = GridBagConstraints.BOTH;
		this.mSkipWaitingInReplayButton = new JButton(
				INIT_SKIP_WAITING_IN_REPLAY_BUTTON_TEXT);
		this.mSkipWaitingInReplayButton.addActionListener(this);
		this.mHistoryButtonBarPanel.add(this.mSkipWaitingInReplayButton, c);
	}

	private void updateUI() {
		this._updateStatusBar();
		this._updateButtonBar();
		this._updateRecordFileBar();
		this._updateResponseFileBar();
		this._updateHistoryTextBarPanel();
		this._updateHistoryButtonBarPanel();
	}

	private void _updateStatusBar() {
		// StatusLabel
		int stateType = this.mControllerState.getType();
		switch (stateType) {
		case ControllerState.Type.INITIAL: {
			this.mStatusLabel.setText("Waiting for connection...");
		}
			break;
		case ControllerState.Type.CONNECTED: {
			int mirroredStateType = this.mMirroredServiceState.serviceStateType;
			switch (mirroredStateType) {
			case RecordroidServiceState.ServiceStateType.IDLE: {
				this.mStatusLabel.setText("Connected");
			}
				break;
			case RecordroidServiceState.ServiceStateType.RECORDING: {
				this.mStatusLabel.setText("Recording...");
			}
				break;
			case RecordroidServiceState.ServiceStateType.PREPARING_TO_REPLAY: {
				this.mStatusLabel.setText("Preparing to replay...");
			}
				break;
			case RecordroidServiceState.ServiceStateType.REPLAYING: {
				this.mStatusLabel.setText("Replaying...");
			}
				break;
			}
		}
			break;
		case ControllerState.Type.DISCONNECTED: {
			this.mStatusLabel.setText("Disconnected!");
		}
			break;
		case ControllerState.Type.WAITING_FOR_STATE_CHANGE: {
			this.mStatusLabel.setText("Wait a minute...");
		}
			break;
		}
	}

	private void _updateButtonBar() {
		// RecordButton & ReplayButton
		int stateType = this.mControllerState.getType();
		ImageIcon recordIcon = new ImageIcon(getClass().getResource(
				"/record_icon.png"));
		ImageIcon replayIcon = new ImageIcon(getClass().getResource(
				"/replay_icon.png"));
		ImageIcon stopIcon = new ImageIcon(getClass().getResource(
				"/stop_icon.png"));
		switch (stateType) {
		case ControllerState.Type.INITIAL: {
			this.mRecordButton.setEnabled(false);
			this.mReplayButton.setEnabled(false);
			this.mRecordButton.setIcon(recordIcon);
			this.mReplayButton.setIcon(replayIcon);
		}
			break;
		case ControllerState.Type.DISCONNECTED: {
			this.mRecordButton.setEnabled(false);
			this.mReplayButton.setEnabled(false);
		}
			break;
		case ControllerState.Type.WAITING_FOR_STATE_CHANGE: {
			this.mRecordButton.setEnabled(false);
			this.mReplayButton.setEnabled(false);
		}
			break;
		case ControllerState.Type.CONNECTED: {
			int mirroredStateType = this.mMirroredServiceState.serviceStateType;
			switch (mirroredStateType) {
			case RecordroidServiceState.ServiceStateType.IDLE: {
				this.mRecordButton.setEnabled(true);
				this.mReplayButton.setEnabled(true);
				this.mRecordButton.setIcon(recordIcon);
				this.mReplayButton.setIcon(replayIcon);
			}
				break;
			case RecordroidServiceState.ServiceStateType.RECORDING: {
				this.mRecordButton.setEnabled(true);
				this.mReplayButton.setEnabled(false);
				this.mRecordButton.setIcon(stopIcon);
			}
				break;
			case RecordroidServiceState.ServiceStateType.REPLAYING: {
				this.mRecordButton.setEnabled(false);
				this.mReplayButton.setEnabled(true);
				this.mReplayButton.setIcon(stopIcon);
			}
				break;
			case RecordroidServiceState.ServiceStateType.PREPARING_TO_REPLAY: {
				this.mRecordButton.setEnabled(false);
				this.mReplayButton.setEnabled(false);
				this.mReplayButton.setIcon(stopIcon);
			}
				break;
			}
		}
			break;
		}
	}

	private void _updateRecordFileBar() {
		// SelectRecordFileButton
		int stateType = this.mControllerState.getType();
		switch (stateType) {
		case ControllerState.Type.CONNECTED: {
			int mirroredStateType = this.mMirroredServiceState.serviceStateType;
			switch (mirroredStateType) {
			case RecordroidServiceState.ServiceStateType.IDLE: {
				this.mSelectRecordFileButton.setEnabled(true);
			}
				break;
			default: {
				this.mSelectRecordFileButton.setEnabled(false);
			}
				break;
			}
		}
			break;
		case ControllerState.Type.INITIAL:
		case ControllerState.Type.WAITING_FOR_STATE_CHANGE:
		case ControllerState.Type.DISCONNECTED: {
			this.mSelectRecordFileButton.setEnabled(false);
		}
			break;
		}

		// RecordFilePathLabel
		String filePathLabelText;
		File file = this.mRecordFile.getFile();
		if (file != null)
			filePathLabelText = file.getName();
		else
			filePathLabelText = INIT_RECORD_FILE_PATH_LABEL_TEXT;
		this.mRecordFilePathLabel.setText(filePathLabelText);
	}

	private void _updateResponseFileBar() {
		// SelectResponseFileButton, DisableResponseFileButton
		int stateType = this.mControllerState.getType();
		switch (stateType) {
		case ControllerState.Type.CONNECTED: {
			int mirroredStateType = this.mMirroredServiceState.serviceStateType;
			switch (mirroredStateType) {
			case RecordroidServiceState.ServiceStateType.IDLE: {
				this.mSelectResponseFileButton.setEnabled(true);
				this.mDisableResponseFileButton.setEnabled(true);
			}
				break;
			default: {
				this.mSelectResponseFileButton.setEnabled(false);
				this.mDisableResponseFileButton.setEnabled(false);
			}
				break;
			}
		}
			break;
		case ControllerState.Type.INITIAL:
		case ControllerState.Type.WAITING_FOR_STATE_CHANGE:
		case ControllerState.Type.DISCONNECTED: {
			this.mSelectResponseFileButton.setEnabled(false);
			this.mDisableResponseFileButton.setEnabled(false);
		}
			break;
		}

		// ResponseFilePathLabel
		String filePathLabelText;
		File file = this.mResponseFile.getFile();
		if (file != null)
			filePathLabelText = file.getName();
		else
			filePathLabelText = INIT_RESPONSE_FILE_PATH_LABEL_TEXT;
		this.mResponseFilePathLabel.setText(filePathLabelText);
	}

	private void _updateHistoryTextBarPanel() {
		synchronized (this.mHistoryArray) {
			synchronized (this.mHistoryTextDirty) {
				if (this.mHistoryTextDirty == true) {
					this.mHistoryTextPane.setText(this.mHistoryText);
					this.mHistoryTextPane
							.setCaretPosition(this.mHistoryTextPane
									.getDocument().getLength());
					this.mHistoryTextDirty = false;
				}
			}
		}
	}

	private void _updateHistoryButtonBarPanel() {
		// ClearHistoryButton
		if (this.mHistoryArray.size() == 0)
			this.mClearHistoryButton.setEnabled(false);
		else
			this.mClearHistoryButton.setEnabled(true);

		// SkipWaitingInReplay
		int controllerStateType = this.mControllerState.getType();
		switch (controllerStateType) {
		case ControllerState.Type.CONNECTED: {
			int serviceStateType = this.mMirroredServiceState.serviceStateType;
			switch (serviceStateType) {
			case RecordroidServiceState.ServiceStateType.REPLAYING: {
				this.mSkipWaitingInReplayButton.setEnabled(true);
			}
				break;
			default: {
				this.mSkipWaitingInReplayButton.setEnabled(false);
			}
				break;
			}
		}
			break;
		default: {
			this.mSkipWaitingInReplayButton.setEnabled(false);
		}
			break;
		}
	}

	private void makeDialog(String title, String message, int iconOption) {
		JOptionPane.showMessageDialog(this.mFrame, message, "Recordroid - "
				+ title, iconOption);
	}

	private boolean onSelectRecordFileButtonClicked() {
		String presentPath = this.mPrefs.get(
				PreferenceConstants.KEY_RECORD_FILECHOOSER_PATH,
				PreferenceConstants.INIT_RECORD_FILECHOOSER_PATH);
		if(new File(presentPath).exists() == false)
			presentPath = PreferenceConstants.INIT_RECORD_FILECHOOSER_PATH;
		JFileChooser fileChooser = new JFileChooser(presentPath);
		int returnVal = fileChooser.showDialog(this.mFrame,
				RECORD_FILECHOOSER_BUTTON_TEXT);
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			File selectedFile = fileChooser.getSelectedFile();

			// Set preferences
			try {
				this.mPrefs.put(
						PreferenceConstants.KEY_RECORD_FILECHOOSER_PATH,
						selectedFile.getParent());
				this.mPrefs.flush();
			} catch (BackingStoreException e) {
			}

			// Set a new file selected by file chooser
			try {
				File file = selectedFile;
				if (file.exists() == false)
					file.createNewFile();
				if (file.canWrite() == false || file.canRead() == false) {
					makeDialog("File open error", "Cannot access file: "
							+ selectedFile.getAbsolutePath(),
							JOptionPane.ERROR_MESSAGE);
				}
				this.mRecordFile.setFile(file);
				this.updateUI();
				return true;
			} catch (FileNotFoundException e) {
				makeDialog("File open error", "Cannot find file "
						+ selectedFile.getAbsolutePath(),
						JOptionPane.ERROR_MESSAGE);
			} catch (IOException e) {
				makeDialog("File open error", "I/O exception in opening "
						+ selectedFile.getAbsolutePath(),
						JOptionPane.ERROR_MESSAGE);
			}
		}
		return false;
	}

	private void onRecordButtonClicked() {
		int stateType = this.mControllerState.getType();
		switch (stateType) {
		case ControllerState.Type.CONNECTED: {
			// when connected
			int mirroredStateType = this.mMirroredServiceState.serviceStateType;
			switch (mirroredStateType) {
			case RecordroidServiceState.ServiceStateType.IDLE: {
				// Idle: turn on recording
				this.turnOnRecord();
			}
				break;
			case RecordroidServiceState.ServiceStateType.RECORDING: {
				// Recording: turn off recording
				this.turnOffRecord();
			}
				break;
			}
		}
			break;
		}
	}

	private void onReplayButtonClicked() {
		// send messages to target device's event buffer initially
		int stateType = this.mControllerState.getType();
		switch (stateType) {
		case ControllerState.Type.CONNECTED: {
			// when connected
			int mirroredStateType = this.mMirroredServiceState.serviceStateType;
			switch (mirroredStateType) {
			case RecordroidServiceState.ServiceStateType.IDLE: {
				// Idle: turn on replaying
				this.turnOnReplay();
			}
				break;
			case RecordroidServiceState.ServiceStateType.REPLAYING: {
				// Recording: turn off replaying
				this.turnOffReplay();
			}
				break;
			}
		}
			break;
		}
	}

	private boolean onSelectResponseFileButtonClicked() {
		String presentPath = this.mPrefs.get(
				PreferenceConstants.KEY_RESPONSE_FILECHOOSER_PATH,
				PreferenceConstants.INIT_RESPONSE_FILECHOOSER_PATH);
		if(new File(presentPath).exists() == false)
			presentPath = PreferenceConstants.INIT_RESPONSE_FILECHOOSER_PATH;
		JFileChooser fileChooser = new JFileChooser(presentPath);
		int returnVal = fileChooser.showDialog(this.mFrame,
				RESPONSE_FILECHOOSER_BUTTON_TEXT);
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			File selectedFile = fileChooser.getSelectedFile();

			// Set preferences
			try {
				this.mPrefs.put(
						PreferenceConstants.KEY_RESPONSE_FILECHOOSER_PATH,
						selectedFile.getParent());
				this.mPrefs.flush();
			} catch (BackingStoreException e) {
			}

			// Set a new file selected by file chooser
			try {
				File file = selectedFile;
				if (file.exists() == false)
					file.createNewFile();
				if (file.canWrite() == false || file.canRead() == false) {
					makeDialog("File open error", "Cannot access file: "
							+ selectedFile.getAbsolutePath(),
							JOptionPane.ERROR_MESSAGE);
				}
				this.mResponseFile.setFile(file);
				this.updateUI();
				return true;
			} catch (FileNotFoundException e) {
				makeDialog("File open error", "Cannot find file "
						+ selectedFile.getAbsolutePath(),
						JOptionPane.ERROR_MESSAGE);
			} catch (IOException e) {
				makeDialog("File open error", "I/O exception in opening "
						+ selectedFile.getAbsolutePath(),
						JOptionPane.ERROR_MESSAGE);
			}
		}
		return false;
	}

	private void onDisableResponseFileButtonClicked() {
		this.mResponseFile.setFile(null);
	}

	private void onOptionBuuttonClicked() {
		new OptionFrameController("Options - Recordroid", this.mPrefs);
	}

	private void onClearHistoryButtonClicked() {
		this.clearHistory();
	}

	private void onSkipWaitingInReplayClicked() {
		this.skipWaitingInReplay();
	}

	private void turnOnRecord() {
		// if file is not selected, do followed actions by the button
		if (this.mRecordFile.getFile() == null) {
			boolean selected = this.onSelectRecordFileButtonClicked();
			if (selected == false)
				return;
		}

		// Open file for writing
		try {
			this.mRecordFile.openWriting();
		} catch (FileNotFoundException e) {
			makeDialog("File open error", "Cannot find file "
					+ this.mRecordFile.getFile().getAbsolutePath(),
					JOptionPane.ERROR_MESSAGE);
			return;
		} catch (IOException e) {
			makeDialog("File open error", "I/O exception in opening "
					+ this.mRecordFile.getFile().getAbsolutePath(),
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		// state change: waiting for recording
		this.mControllerState
				.setType(ControllerState.Type.WAITING_FOR_STATE_CHANGE);
		this.updateUI();

		// send a message to target device
		RecordroidCommand command = RecordroidCommand.makeRecordingOn();
		;
		this.mUSBConnector.sendMessage(command);
		this.addHistoryLine("Record ON", HISTORY_TEXT_COLOR_COMMAND, true);
	}

	private void turnOffRecord() {
		// state change: waiting for idle
		this.mControllerState
				.setType(ControllerState.Type.WAITING_FOR_STATE_CHANGE);
		this.updateUI();

		// send a message to target device
		RecordroidCommand command = RecordroidCommand.makeRecordingOff();
		this.mUSBConnector.sendMessage(command);
		this.addHistoryLine("Record OFF", HISTORY_TEXT_COLOR_COMMAND, true);
	}

	private void turnOnReplay() {
		// if file is not selected, do followed actions by the button
		if (this.mRecordFile.getFile() == null) {
			boolean selected = this.onSelectRecordFileButtonClicked();
			if (selected == false)
				return;
		}

		// Initialize fields for replaying
		this.mPrevRunningSN = -1;
		this.mPrevReplayBufferIndex = -1;

		// Open file for reading
		try {
			this.mRecordFile.openReading();
		} catch (FileNotFoundException e) {
			makeDialog("File open error", "Cannot find file "
					+ this.mRecordFile.getFile().getAbsolutePath(),
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		// Open file for writing response file
		if (this.mResponseFile.getFile() != null) {
			try {
				this.mResponseFile.openWriting();
			} catch (FileNotFoundException e) {
				makeDialog("File open error", "Cannot find file "
						+ this.mRecordFile.getFile().getAbsolutePath(),
						JOptionPane.ERROR_MESSAGE);
				return;
			} catch (IOException e) {
				makeDialog("File open error", "I/O exception in opening "
						+ this.mRecordFile.getFile().getAbsolutePath(),
						JOptionPane.ERROR_MESSAGE);
				return;
			}
		}

		// state change: waiting for replaying
		this.mControllerState
				.setType(ControllerState.Type.WAITING_FOR_STATE_CHANGE);
		this.updateUI();

		// send a message to target device
		int minimumPreloadSize = this.mPrefs.getInt(
				PreferenceConstants.KEY_MINIMUM_PRELOAD_SIZE,
				PreferenceConstants.INIT_MINIMUM_PRELOAD_SIZE);
		int maximumSleepUS = this.mPrefs.getInt(
				PreferenceConstants.KEY_MAXIMUM_SLEEP_MS,
				PreferenceConstants.INIT_MAXIMUM_SLEEP_MS);

		RecordroidCommand command = RecordroidCommand.makeReplayingOn(
				minimumPreloadSize, maximumSleepUS);

		this.mUSBConnector.sendMessage(command);
		this.addHistoryLine("Replay ON", HISTORY_TEXT_COLOR_COMMAND, true);
	}

	private void turnOffReplay() {
		// state change: waiting for idle
		this.mControllerState
				.setType(ControllerState.Type.WAITING_FOR_STATE_CHANGE);
		this.updateUI();

		// Stop reading record file & close it
		try {
			this.mRecordFile.close();
		} catch (IOException e) {
			System.err.println("File close failed" + e);
		}

		// send a message to target device
		RecordroidCommand command = RecordroidCommand.makeReplayingOff();
		this.mUSBConnector.sendMessage(command);
		this.addHistoryLine("Replay OFF", HISTORY_TEXT_COLOR_COMMAND, true);
	}

	private void skipWaitingInReplay() {
		int stateType = this.mControllerState.getType();
		switch (stateType) {
		case ControllerState.Type.CONNECTED: {
			int serviceStateType = this.mMirroredServiceState.serviceStateType;
			switch (serviceStateType) {
			case RecordroidServiceState.ServiceStateType.REPLAYING: {
				RecordroidCommand command = RecordroidCommand
						.makeSkipWaitingInReplay();
				this.mUSBConnector.sendMessage(command);
				this.addHistoryLine("Skip waiting", HISTORY_TEXT_COLOR_COMMAND,
						true);
			}
				break;
			}
		}
			break;
		}
	}

	private void onDirtyLaunch(RecordroidServiceState initialState) {
		// Dirty Launch:
		// Shutdown running behavior that is started before this
		// controller's launch
		int serviceStateType = initialState.serviceStateType;
		switch (serviceStateType) {
		case RecordroidServiceState.ServiceStateType.RECORDING: {
			this.turnOffRecord();
			this.addHistoryLine(
					"Target already recording... It turns OFF by force.",
					HISTORY_TEXT_COLOR_WARNING, true);
		}
			break;
		case RecordroidServiceState.ServiceStateType.PREPARING_TO_REPLAY:
		case RecordroidServiceState.ServiceStateType.REPLAYING: {
			this.turnOffReplay();
			this.addHistoryLine(
					"Target already replaying... It turns OFF by force.",
					HISTORY_TEXT_COLOR_WARNING, true);
		}
			break;
		}
	}

	private void onReceiveServiceState(RecordroidServiceState newState) {
		// FIXED: accessing & updating 'state' should be serialized
		synchronized (newState) {
			int oldStateType = -1;
			if (this.mMirroredServiceState == null) {
				this.mMirroredServiceState = newState;

				this.onDirtyLaunch(newState);
			} else {
				oldStateType = this.mMirroredServiceState.serviceStateType;
				this.mMirroredServiceState.serviceStateType = newState.serviceStateType;
			}

			// When target state is changed, do more behaviors.
			if (oldStateType != newState.serviceStateType) {
				this.onChangedServiceStateType(oldStateType,
						newState.serviceStateType);
			}

			// Change controller's state by service state
			if (newState.serviceStateType == RecordroidServiceState.ServiceStateType.PREPARING_TO_REPLAY) {
				this.mControllerState
						.setType(ControllerState.Type.WAITING_FOR_STATE_CHANGE);
			} else {
				this.mControllerState.setType(ControllerState.Type.CONNECTED);
			}

			// When target state has fields, do more behaviors.
			if (newState.serviceStateType == RecordroidServiceState.ServiceStateType.REPLAYING) {
				RecordroidServiceState.ReplayingFields fields = newState.replayingFields;
				if (fields != null)
					this.onServiceStateReplaying(fields);
			}

			this.updateUI();
		}
	}

	private void onChangedServiceStateType(int oldStateType, int newStateType) {
		if (oldStateType == RecordroidServiceState.ServiceStateType.RECORDING
				&& newStateType == RecordroidServiceState.ServiceStateType.IDLE) {
			// FIXED: Mark that present record file should be closed.
			// After this USB message handler is finished, file will be closed.
			this.mShouldCloseRecordFile = true;
		} else if (oldStateType == RecordroidServiceState.ServiceStateType.REPLAYING
				&& newStateType == RecordroidServiceState.ServiceStateType.IDLE) {
			// Stop reading record file & close it
			this.mShouldCloseResponseFile = true;
			try {
				this.mRecordFile.close();
			} catch (IOException e) {
				System.err.println("File close failed" + e);
			}
		}

		// History
		switch (newStateType) {
		case RecordroidServiceState.ServiceStateType.IDLE: {
			this.addHistoryLine("Service: idle", HISTORY_TEXT_COLOR_INFO, true);
		}
			break;
		case RecordroidServiceState.ServiceStateType.PREPARING_TO_REPLAY: {
			this.addHistoryLine("Service: preparing to replay",
					HISTORY_TEXT_COLOR_INFO, true);
		}
			break;
		case RecordroidServiceState.ServiceStateType.RECORDING: {
			this.addHistoryLine("Service: recording", HISTORY_TEXT_COLOR_INFO,
					true);
		}
			break;
		case RecordroidServiceState.ServiceStateType.REPLAYING: {
			this.addHistoryLine("Service: replaying", HISTORY_TEXT_COLOR_INFO,
					true);
		}
			break;
		}
	}

	private void onServiceStateReplaying(
			RecordroidServiceState.ReplayingFields fields) {
		if (this.mRecordFile.getFile() == null)
			return;

		// In the case that target is replaying in actually
		if (fields.presentReplayBufferSize != 0) {
			this.addHistoryLine("Replay: " + fields.presentReplayBufferIndex
					+ "/" + fields.presentReplayBufferSize + " (SN: "
					+ fields.runningSN + ")", HISTORY_TEXT_COLOR_INFO, true);

			// Determine if 'skip waiting in replay' button should be enabled or
			// not
			if ((this.mPrevRunningSN == fields.runningSN)
					&& (this.mPrevReplayBufferIndex == fields.presentReplayBufferIndex)) {

			} else {

			}
			this.mPrevRunningSN = fields.runningSN;
			this.mPrevReplayBufferIndex = fields.presentReplayBufferIndex;
		}

		long requiredSN = fields.requiredSN;
		// Require replay buffer from record file
		TraceFile.Reader.TempBuffer tempBuffer = this.mRecordFile
				.requireReplayBuffer(requiredSN);
		if (tempBuffer != null) {
			// Send 'fill replay buffer' command to targetType
			RecordroidCommand cmd = RecordroidCommand.makeFillReplayBuffer(
					requiredSN, tempBuffer.events.size(),
					(tempBuffer.isAllReadDone == false));
			this.addHistoryLine(tempBuffer.events.size()
					+ " Sending events... (SN: " + requiredSN + ")",
					HISTORY_TEXT_COLOR_COMMAND, true);
			mUSBConnector.sendMessage(cmd);

			// Send all events of TempBuffer to target
			for (RecordroidEvent e : tempBuffer.events) {
				mUSBConnector.sendMessage(e);
			}
		}
	}

	private void putRequestStateMessage() {
		if (this.mUSBConnector.getSendBufferLength() == 0) {
			RecordroidCommand newCmd = RecordroidCommand.makeRequestState();
			this.mUSBConnector.sendMessage(newCmd);
		}
	}

	private void addHistoryLine(String historyLineBody, String color,
			boolean updateUIImmediately) {
		synchronized (this.mHistoryArray) {
			int historyTextLimit = this.mPrefs.getInt(
					PreferenceConstants.KEY_HISTORY_TEXT_LIMIT,
					PreferenceConstants.INIT_HISTORY_TEXT_LIMIT);
			if (this.mHistoryArray.size() >= historyTextLimit) {
				int exceed = this.mHistoryArray.size() - historyTextLimit + 1;
				int exceedLength = 0;
				for (int i = 0; i < exceed; i++) {
					exceedLength += this.mHistoryArray.get(i).length();

				}
				for (int i = 0; i < exceed; i++) {
					this.mHistoryArray.remove(0);
				}
				if (exceedLength >= this.mHistoryText.length())
					this.mHistoryText = "";
				else
					this.mHistoryText = this.mHistoryText.substring(
							exceedLength, this.mHistoryText.length() - 1);
			}
			String historyLine = "<font color=" + color + ">" + historyLineBody
					+ "</font><br />";
			this.mHistoryArray.add(historyLine);
			this.mHistoryText = this.mHistoryText + historyLine;

			synchronized (this.mHistoryTextDirty) {
				this.mHistoryTextDirty = true;
			}
			
			if (updateUIImmediately == true)
				this.updateUI();
		}
	}

	private void clearHistory() {
		synchronized (this.mHistoryArray) {
			this.mHistoryText = "";
			this.mHistoryArray.clear();
			this.updateUI();
			this.mHistoryTextDirty = true;
		}
	}

	// Implements ActionListener
	@Override
	public void actionPerformed(ActionEvent event) {
		if (event.getSource() == mSelectRecordFileButton) {
			onSelectRecordFileButtonClicked();
		} else if (event.getSource() == mRecordButton) {
			onRecordButtonClicked();
		} else if (event.getSource() == mReplayButton) {
			onReplayButtonClicked();
		} else if (event.getSource() == mSelectResponseFileButton) {
			onSelectResponseFileButtonClicked();
		} else if (event.getSource() == mDisableResponseFileButton) {
			onDisableResponseFileButtonClicked();
		} else if (event.getSource() == mOptionButton) {
			onOptionBuuttonClicked();
		} else if (event.getSource() == mClearHistoryButton) {
			onClearHistoryButtonClicked();
		} else if (event.getSource() == mSkipWaitingInReplayButton) {
			onSkipWaitingInReplayClicked();
		}
	}

	// Implements USBMessageListener
	@Override
	public void onUSBMessage(ArrayList<RecordroidMessage> messages) {
		if (messages == null)
			return;
		for (RecordroidMessage msg : messages) {
			try {
				if (msg instanceof RecordroidKernelInputEvent) {
					if (this.mRecordFile != null) {
						RecordroidEvent event = (RecordroidEvent) msg;
						this.mRecordFile.writeEvent(event);
					}
				} else if (msg instanceof RecordroidPlatformEvent) {
					if (this.mRecordFile != null) {
						RecordroidEvent event = (RecordroidEvent) msg;
						this.mRecordFile.writeEvent(event);
					}
					if (this.mResponseFile != null) {
						RecordroidPlatformEvent event = (RecordroidPlatformEvent) msg;
						this.mResponseFile.writeEvent(event);
					}
				} else if (msg instanceof RecordroidServiceState) {
					// Listen target's state
					RecordroidServiceState serviceState = (RecordroidServiceState) msg;
					this.onReceiveServiceState(serviceState);
				}
			} catch (IOException e) {
				this.makeDialog("Write file error",
						"I/O Exception during writing record file\n" + e,
						JOptionPane.ERROR_MESSAGE);
			}
		}

		// FIXED: after target's recording is done, record file should be
		// closed.
		if (this.mShouldCloseRecordFile) {
			try {
				this.mRecordFile.close();
				this.mControllerState.setType(ControllerState.Type.CONNECTED);
				this.mShouldCloseRecordFile = false;
			} catch (IOException e) {
				System.err.println("File close failed" + e);
			}
		}
		if (this.mShouldCloseResponseFile) {
			try {
				this.mResponseFile.close();
				this.mShouldCloseResponseFile = false;
			} catch (IOException e) {
				System.err.println("File close failed" + e);
			}
		}
	}

	@Override
	public void willDoUSBConnectorRoutine() {
		// Fetch a request state message to send buffer
		this.putRequestStateMessage();
	}

	@Override
	public void didUSBConnectorRoutine() {
		// Ignore
	}

	// Implements ADBConnectionListener
	@Override
	public void onADBConnect() {
		// start USB client connector
		this.mUSBConnector.start();
		this.addHistoryLine("Target found. Waiting for Recordroid Service...",
				HISTORY_TEXT_COLOR_INFO, true);
	}

	@Override
	public void onADBDisconnect() {
		// stop USB client connector
		this.mUSBConnector.stop();
		this.mControllerState.setType(ControllerState.Type.DISCONNECTED);
		this.addHistoryLine("Target not found! Waiting for target...",
				HISTORY_TEXT_COLOR_WARNING, false);
		this.updateUI();
	}

	// Implements WindowListener
	@Override
	public void windowActivated(WindowEvent arg0) {
	}

	@Override
	public void windowClosed(WindowEvent arg0) {
	}

	@Override
	public void windowClosing(WindowEvent arg0) {
		try {
			this.mPrefs.putInt(PreferenceConstants.KEY_MAIN_FRAME_WINDOW_X,
					this.mFrame.getLocation().x);
			this.mPrefs.putInt(PreferenceConstants.KEY_MAIN_FRAME_WINDOW_Y,
					this.mFrame.getLocation().y);
			this.mPrefs.flush();
		} catch (BackingStoreException e) {
		}
	}

	@Override
	public void windowDeactivated(WindowEvent arg0) {
	}

	@Override
	public void windowDeiconified(WindowEvent arg0) {
	}

	@Override
	public void windowIconified(WindowEvent arg0) {
	}

	@Override
	public void windowOpened(WindowEvent arg0) {
	}
}
