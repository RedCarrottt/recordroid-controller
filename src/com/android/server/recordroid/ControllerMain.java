package com.android.server.recordroid;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;

import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public class ControllerMain {
	private static final int DEFAULT_TARGET_PORT = 33001;
	private static final String LOCK_FILE_NAME = ".lock";

	public static void main(String[] args) {
		boolean success = lockInstance(LOCK_FILE_NAME);
		if (success) {
			openMainFrame();
		} else {
			JOptionPane.showMessageDialog(null,
					"Recordroid Controller is already running!", "Recordroid",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private static void openMainFrame() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (UnsupportedLookAndFeelException e) {
			e.printStackTrace();
		}
		new MainFrameController("Recordroid", DEFAULT_TARGET_PORT);
	}

	private static boolean lockInstance(final String lockFile) {
		try {
			final File file = new File(lockFile);
			final RandomAccessFile randomAccessFile = new RandomAccessFile(
					file, "rw");
			final FileLock fileLock = randomAccessFile.getChannel().tryLock();
			if (fileLock != null) {
				Runtime.getRuntime().addShutdownHook(new Thread() {
					public void run() {
						try {
							fileLock.release();
							randomAccessFile.close();
							file.delete();
						} catch (Exception e) {
							System.err.println("Unable to remove lock file: "
									+ lockFile + "\n" + e);
						}
					}
				});
				return true;
			}
		} catch (Exception e) {
			System.err.println("Unable to create and/or lock file: " + lockFile
					+ "\n" + e);
		}
		return false;
	}
}
