package com.android.server.recordroid;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.prefs.Preferences;

public class TraceFile {
	private File mFile = null;
	private Writer mWriter = null;
	private Reader mReader = null;

	private Preferences mPrefs;

	public TraceFile(Preferences prefs) {
		this.mPrefs = prefs;
	}

	public File getFile() {
		return this.mFile;
	}

	public void setFile(File file) {
		this.mFile = file;
	}

	public void openWriting() throws FileNotFoundException, IOException {
		if (this.mFile == null)
			throw new FileNotFoundException();
		BufferedWriter bufWriter = new BufferedWriter(
				new FileWriter(this.mFile));
		this.mWriter = new Writer(bufWriter);
	}

	public void openReading() throws FileNotFoundException {
		if (this.mFile == null)
			throw new FileNotFoundException();
		BufferedReader bufReader = new BufferedReader(
				new FileReader(this.mFile));
		// CAUTION: initializing reader results in initial-preloading on preload
		// thread!
		this.mReader = new Reader(bufReader);
	}

	public void close() throws IOException {
		if (this.mWriter != null) {
			this.mWriter.close();
			this.mWriter = null;
		} else if (this.mReader != null) {
			this.mReader.close();
			this.mReader = null;
		}
	}

	public void writeEvent(RecordroidEvent event) throws IOException {
		if (this.mWriter != null) {
			this.mWriter.writeEvent(event);
		} else {
			// Ignore
		}
	}

	public Reader.TempBuffer requireReplayBuffer(long requiredSN) {
		if (this.mReader == null)
			return null;
		else
			return this.mReader.requireReplayBuffer(requiredSN);
	}

	class Writer {
		private BufferedWriter mWriter;
		private Lock mLock = new ReentrantLock();

		public Writer(BufferedWriter bufWriter) {
			this.mWriter = bufWriter;
		}

		public void close() throws IOException {
			// Flush and Close the file
			this.mLock.lock();
			try {
				this.mWriter.flush();
				this.mWriter.close();
			} finally {
				this.mLock.unlock();
			}

			// Post-record Process
			this.doPostRecordProcess();
		}

		// onListenEvent: RecordroidEvent -> String -> File
		public void writeEvent(RecordroidEvent event) throws IOException {
			this.mLock.lock();
			try {
				String traceStr = event.toStringForTraceFile();
				if(event instanceof RecordroidPlatformEvent == false)
					this.mWriter.write(traceStr + "\n");
			} finally {
				this.mLock.unlock();
			}
		}

		private void doPostRecordProcess() throws IOException {
			// Step 1. Read all events from record file
			ArrayList<RecordroidEvent> events = new ArrayList<RecordroidEvent>();
			BufferedReader readBuffer = new BufferedReader(
					new FileReader(mFile));
			String strLine;
			while (true) {
				strLine = readBuffer.readLine();
				if (strLine == null)
					break;

				if (RecordroidKernelInputEvent.transformableFrom(strLine)) {
					RecordroidKernelInputEvent event;
					event = RecordroidKernelInputEvent
							.makeFromStringForTraceFile(strLine);
					events.add(event);
				} else if (RecordroidPlatformEvent.transformableFrom(strLine)) {
					RecordroidPlatformEvent event;
					event = RecordroidPlatformEvent
							.makeFromStringForTraceFile(strLine);
					events.add(event);
				}
			}
			readBuffer.close();

			if (events.isEmpty() == true)
				return;

			// Step 2. Sort them
			BufferedWriter writeBuffer = new BufferedWriter(new FileWriter(
					mFile));
			TimestampSort sort = new TimestampSort();
			Collections.sort(events, sort);

			// Step 3. Convert them to relative timestamp form
			// First relative timestamp should always be 0.
			long prevTimestampUS = events.get(0).timestampUS;
			for (RecordroidEvent event : events) {
				long thisTimestampUS = event.timestampUS;
				long newTimestampUS = thisTimestampUS - prevTimestampUS;
				prevTimestampUS = thisTimestampUS;
				event.timestampUS = newTimestampUS;
			}

			// Step 4. Write them
			for (RecordroidEvent e : events) {
				String traceStr = e.toStringForTraceFile();
				if(e instanceof RecordroidPlatformEvent == false)
					this.mWriter.write(traceStr + "\n");
			}
			writeBuffer.flush();
			writeBuffer.close();
		}

		class TimestampSort implements Comparator<RecordroidEvent> {
			public int compare(RecordroidEvent e1, RecordroidEvent e2) {
				return (e1.timestampUS > e2.timestampUS) ? 1
						: ((e1.timestampUS == e2.timestampUS) ? 0 : -1);
			}
		}
	}

	class Reader {
		private BufferedReader mReader;
		private PreloadBuffer mPreloadBuffer;
		private PreloadWorker mPreloadWorker;
		private long mLastSentSN;
		private boolean mIsAllReadDone;

		public Reader(BufferedReader bufReader) {
			this.mReader = bufReader;
			this.mPreloadBuffer = new PreloadBuffer();
			this.mLastSentSN = 0;
			this.mIsAllReadDone = false;

			// Do preload first events chunk
			this.doPreload();
		}

		public void close() throws IOException {
			this.mReader.close();
		}

		public TempBuffer requireReplayBuffer(long requiredSN) {
			// Called when request of replay buffer is came from target device
			if (this.mLastSentSN < requiredSN) {
				TempBuffer tempBuffer = new TempBuffer();
				try {
					while (true) {
						// Check if preloading was successful
						this.mPreloadBuffer.mLock.lock();
						if (this.mPreloadBuffer.events != null)
							break;
						this.mPreloadBuffer.mLock.unlock();
						// EMERGENCY LOADING:
						// If preloading was failed, do load record file
						// manually,
						// until success
						try {
							System.err
									.println("Preload failed! retry to preload...");
							this.doPreload();

							// Emergency loading should be done synchronously.
							this.mPreloadWorker.join();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}

					// Move events of PreloadBuffer to TempBuffer
					tempBuffer.events = this.mPreloadBuffer.events;
					tempBuffer.isAllReadDone = this.mIsAllReadDone;

					// Clear PreloadBuffer
					this.mPreloadBuffer.events = null;

					// Increase 'LastSentSN'
					this.mLastSentSN++;

				} finally {
					this.mPreloadBuffer.mLock.unlock();
				}

				// Do next preloading
				this.doPreload();

				return tempBuffer;
			} else {
				// Ignore if required chunk have already been sent to target
				return null;
			}
		}

		private void doPreload() {
			this.mPreloadWorker = new PreloadWorker();
			this.mPreloadWorker.start();
		}

		class TempBuffer {
			public ArrayList<RecordroidEvent> events;
			public boolean isAllReadDone;
		}

		class PreloadWorker extends Thread {
			@Override
			public void run() {
				try {
					mPreloadBuffer.mLock.lock();
					try {
						// If PreloadBuffer is empty and file read is not done,
						// read events from file.
						if (mPreloadBuffer.events == null
								&& mIsAllReadDone == false) {
							mPreloadBuffer.events = new ArrayList<RecordroidEvent>();
							long startTS = 0;
							long lastTS = 0;

							long minimumPreloadIntervalUS = mPrefs
									.getLong(
											PreferenceConstants.KEY_MINIMUM_PRELOAD_INTERVAL_US,
											PreferenceConstants.INIT_MINIMUM_PRELOAD_INTERVAL_US);
							int minimumPreloadSize = mPrefs
									.getInt(PreferenceConstants.KEY_MINIMUM_PRELOAD_SIZE,
											PreferenceConstants.INIT_MINIMUM_PRELOAD_SIZE);

							// 1) read file to the EOF
							// 2) events less than {minimumPreloadSize}
							// 3) (lastTS-startTS) less than
							// {TIMESTAMP_THRESHOLD}
							// 1 && (2 || 3)
							String strLine = mReader.readLine();
							while ((strLine != null)
									&& ((mPreloadBuffer.events.size() < minimumPreloadSize) || ((lastTS - startTS) < minimumPreloadIntervalUS))) {
								// Transform to RecordroidEvent
								RecordroidEvent event = null;
								if (RecordroidKernelInputEvent
										.transformableFrom(strLine)) {
									event = RecordroidKernelInputEvent
											.makeFromStringForTraceFile(strLine);
								} else if (RecordroidPlatformEvent
										.transformableFrom(strLine)) {
									event = RecordroidPlatformEvent
											.makeFromStringForTraceFile(strLine);
								}

								// Handle only 'RecordroidEvent'
								if (event != null) {
									// Add the event to PreloadBuffer
									mPreloadBuffer.events.add(event);

									// Update timestamps
									if (startTS == 0)
										startTS = event.timestampUS;
									lastTS = event.timestampUS;
								}

								// Read a line from file
								strLine = mReader.readLine();
							}

							// If all reads are done, mark it
							if (strLine == null)
								mIsAllReadDone = true;
						}
					} finally {
						mPreloadBuffer.mLock.unlock();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		class PreloadBuffer {
			public ArrayList<RecordroidEvent> events;
			private Lock mLock = new ReentrantLock();

			public PreloadBuffer() {
				this.events = null;
			}
		}
	}
}
