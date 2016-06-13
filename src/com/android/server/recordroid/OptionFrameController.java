package com.android.server.recordroid;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ArrayList;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class OptionFrameController implements WindowListener, ActionListener {
	private static final int INIT_WINDOW_WIDTH = 400;
	private static final int INIT_WINDOW_HEIGHT = 300;
	private static final String PREF_KEY_WINDOW_X = "OptionFrame_Window_X";
	private static final String PREF_KEY_WINDOW_Y = "OptionFrame_Window_Y";
	private static final int INIT_WINDOW_X = 250;
	private static final int INIT_WINDOW_Y = 250;

	public OptionFrameController(String title, Preferences prefs) {
		this.mPrefs = prefs;

		this.initFrame(title);
		this.updateUI();
	}

	// Preferences
	private Preferences mPrefs;

	// Frame & panels
	private String mTitle;
	private JFrame mFrame;
	private JPanel mMainPanel;
	private ArrayList<OptionTuple> mOptionTuples = new ArrayList<OptionTuple>();
	private JButton mCloseButton;
	private final String INIT_CLOSE_BUTTON_TEXT = "Close";

	private void initFrame(String title) {
		int windowX = this.mPrefs.getInt(PREF_KEY_WINDOW_X, INIT_WINDOW_X);
		int windowY = this.mPrefs.getInt(PREF_KEY_WINDOW_Y, INIT_WINDOW_Y);

		this.mTitle = title;
		this.mFrame = new JFrame();
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
		this.mMainPanel.setLayout(new GridBagLayout());
		this.mFrame.getContentPane().add(this.mMainPanel);

		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 1;
		c.fill = GridBagConstraints.BOTH;

		this.initOptionTuples();

		for (OptionTuple tuple : this.mOptionTuples) {
			this.mMainPanel.add(tuple.getPanel(), c);
			c.gridy++;
		}

		this.mCloseButton = new JButton();
		this.mCloseButton.setText(INIT_CLOSE_BUTTON_TEXT);
		this.mCloseButton.addActionListener(this);
		this.mMainPanel.add(this.mCloseButton, c);
	}

	private void initOptionTuples() {
		this.mOptionTuples.add(OptionLabelTuple.makeBold("Controller"));
		this.mOptionTuples.add(OptionFieldTuple.make("History buffer size",
				PreferenceConstants.KEY_HISTORY_TEXT_LIMIT,
				PreferenceConstants.INIT_HISTORY_TEXT_LIMIT, this.mPrefs));
		this.mOptionTuples.add(OptionLabelTuple.make(""));
		this.mOptionTuples.add(OptionLabelTuple
				.makeBold("Sleeping during Replay"));
		this.mOptionTuples.add(OptionFieldTuple.make("Maximum sleep time(ms)",
				PreferenceConstants.KEY_MAXIMUM_SLEEP_MS,
				PreferenceConstants.INIT_MAXIMUM_SLEEP_MS, this.mPrefs));
		this.mOptionTuples.add(OptionLabelTuple.make("<font color=blue>0 means unlimited sleep time.</font>"));
		this.mOptionTuples
				.add(OptionLabelTuple
						.make("<font color=red>Maximum sleep time is applied from next recording.</font>"));
		this.mOptionTuples.add(OptionLabelTuple.make(""));
		this.mOptionTuples.add(OptionLabelTuple.makeBold("Replay Buffer"));
		this.mOptionTuples.add(OptionFieldTuple.make(
				"Minimum replay buffer size(events)",
				PreferenceConstants.KEY_MINIMUM_PRELOAD_SIZE,
				PreferenceConstants.INIT_MINIMUM_PRELOAD_SIZE, this.mPrefs));
		this.mOptionTuples.add(OptionFieldTuple.make(
				"Minimum replay buffer interval(us)",
				PreferenceConstants.KEY_MINIMUM_PRELOAD_INTERVAL_US,
				PreferenceConstants.INIT_MINIMUM_PRELOAD_INTERVAL_US,
				this.mPrefs));
	}

	private void updateUI() {
		// void
	}

	private void flushTuples() {
		for (OptionTuple tuple : this.mOptionTuples) {
			tuple.commit();
		}
	}

	private void backupData() {
		try {
			this.mPrefs.putInt(PREF_KEY_WINDOW_X, this.mFrame.getLocation().x);
			this.mPrefs.putInt(PREF_KEY_WINDOW_Y, this.mFrame.getLocation().y);
			this.flushTuples();
			this.mPrefs.flush();
		} catch (BackingStoreException e) {
		}
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
		this.backupData();
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

	// Implements ActionListener
	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == this.mCloseButton) {
			this.mFrame.dispose();
			this.backupData();
		}
	}
}

interface OptionTuple {
	public JPanel getPanel();

	public void commit();
}

class OptionLabelTuple implements OptionTuple {
	private JPanel mTuplePanel;
	private JLabel mTupleLabel;

	private OptionLabelTuple() {
	}

	public static OptionLabelTuple make(String labelText) {
		OptionLabelTuple ret = new OptionLabelTuple();
		ret.initTuplePanel(labelText, false);
		return ret;
	}

	public static OptionLabelTuple makeBold(String labelText) {
		OptionLabelTuple ret = new OptionLabelTuple();
		ret.initTuplePanel(labelText, true);
		return ret;
	}

	private void initTuplePanel(String labelText, boolean isBold) {
		this.mTuplePanel = new JPanel(new GridBagLayout());

		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 1;
		c.weighty = 1;
		c.fill = GridBagConstraints.BOTH;
		if (isBold)
			labelText = "<b>" + labelText + "</b>";
		this.mTupleLabel = new JLabel("<html>" + labelText + "</html>");
		this.mTuplePanel.add(this.mTupleLabel, c);
	}

	@Override
	public JPanel getPanel() {
		return this.mTuplePanel;
	}

	@Override
	public void commit() {
		// Ignore
	}
}

class OptionFieldTuple implements OptionTuple {
	private JPanel mTuplePanel;
	private JLabel mTupleLabel;
	private JTextField mTupleTextField;

	private String mPrefId;
	private Preferences mPrefs;
	private int mPrefValueType;

	class PrefValueType {
		private static final int PREF_VALUE_TYPE_INTEGER = 1;
		private static final int PREF_VALUE_TYPE_LONG = 2;
		private static final int PREF_VALUE_TYPE_STRING = 3;
	}

	private OptionFieldTuple() {
	}

	// Static constructors
	public static OptionFieldTuple make(String labelText, String prefId,
			int initValue, Preferences prefs) {
		OptionFieldTuple ret = new OptionFieldTuple();
		ret.initFields(prefId, prefs, PrefValueType.PREF_VALUE_TYPE_INTEGER);
		ret.initTuplePanel(labelText);
		ret.initTupleTextField(initValue);
		return ret;
	}

	public static OptionFieldTuple make(String labelText, String prefId,
			long initValue, Preferences prefs) {
		OptionFieldTuple ret = new OptionFieldTuple();
		ret.initFields(prefId, prefs, PrefValueType.PREF_VALUE_TYPE_LONG);
		ret.initTuplePanel(labelText);
		ret.initTupleTextField(initValue);
		return ret;
	}

	public static OptionFieldTuple make(String labelText, String prefId,
			String initValue, Preferences prefs) {
		OptionFieldTuple ret = new OptionFieldTuple();
		ret.initFields(prefId, prefs, PrefValueType.PREF_VALUE_TYPE_STRING);
		ret.initTuplePanel(labelText);
		ret.initTupleTextField(initValue);
		return ret;
	}

	// Initialization
	private void initFields(String prefId, Preferences prefs, int prefValueType) {
		this.mPrefId = prefId;
		this.mPrefs = prefs;
		this.mPrefValueType = prefValueType;
	}

	private void initTuplePanel(String labelText) {
		this.mTuplePanel = new JPanel(new GridBagLayout());

		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 0.70;
		c.weighty = 1;
		c.fill = GridBagConstraints.BOTH;
		this.mTupleLabel = new JLabel(labelText);
		this.mTuplePanel.add(this.mTupleLabel, c);

		c.gridx = 1;
		c.gridy = 0;
		c.weightx = 0.30;
		c.weighty = 1;
		c.fill = GridBagConstraints.BOTH;
		this.mTupleTextField = new JTextField();
		this.mTuplePanel.add(this.mTupleTextField, c);
	}

	private void initTupleTextField(int initValue) {
		this.mTupleTextField.setText(""
				+ this.mPrefs.getInt(this.mPrefId, initValue));
	}

	private void initTupleTextField(long initValue) {
		this.mTupleTextField.setText(""
				+ this.mPrefs.getLong(this.mPrefId, initValue));
	}

	private void initTupleTextField(String initValue) {
		this.mTupleTextField.setText(this.mPrefs.get(this.mPrefId, initValue));
	}

	// Get its panel
	@Override
	public JPanel getPanel() {
		return this.mTuplePanel;
	}

	@Override
	public void commit() {
		String textFieldValue = this.mTupleTextField.getText();
		switch (this.mPrefValueType) {
		case PrefValueType.PREF_VALUE_TYPE_INTEGER: {
			this.mPrefs.putInt(this.mPrefId, Integer.parseInt(textFieldValue));
		}
			break;
		case PrefValueType.PREF_VALUE_TYPE_LONG: {
			this.mPrefs.putLong(this.mPrefId, Long.parseLong(textFieldValue));
		}
			break;
		case PrefValueType.PREF_VALUE_TYPE_STRING: {
			this.mPrefs.put(this.mPrefId, textFieldValue);
		}
			break;
		}
	}
}
