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

public class ResponseFile {
	private File mFile = null;
	private Writer mWriter = null;

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

	public void close() throws IOException {
		if (this.mWriter != null) {
			this.mWriter.close();
			this.mWriter = null;
		}
	}

	public void writeEvent(RecordroidEvent event) throws IOException {
		if (this.mWriter != null) {
			this.mWriter.writeEvent(event);
		} else {
			// Ignore
		}
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
				if (event instanceof RecordroidPlatformEvent) {
					/*RecordroidPlatformEvent platformEvent = (RecordroidPlatformEvent) event;
					String traceStr = platformEvent.toStringForTraceFile();
					if (traceStr.length() != 0)
						this.mWriter.write(traceStr + "\n");*/
				}
			} finally {
				this.mLock.unlock();
			}
		}

		private void doPostRecordProcess() throws IOException {
			// Step 1. Read all events from record file
			ArrayList<RecordroidPlatformEvent> platformEvents = new ArrayList<RecordroidPlatformEvent>();
			BufferedReader readBuffer = new BufferedReader(
					new FileReader(mFile));
			String strLine;
			while (true) {
				strLine = readBuffer.readLine();
				if (strLine == null)
					break;

				if (RecordroidPlatformEvent.transformableFrom(strLine)) {
					RecordroidPlatformEvent platformEvent;
					platformEvent = RecordroidPlatformEvent
							.makeFromStringForTraceFile(strLine);
					platformEvents.add(platformEvent);
				}
			}
			readBuffer.close();

			if (platformEvents.isEmpty() == true)
				return;

			// Step 2. Sort them
			BufferedWriter writeBuffer = new BufferedWriter(new FileWriter(
					mFile));
			TimestampSort sort = new TimestampSort();
			Collections.sort(platformEvents, sort);

			// Step 3. Collect response events by activities and event types
			ArrayList<ActivityResponse> activityResponses = new ArrayList<ActivityResponse>();
			ArrayList<TypeResponse> globalTypeResponses = new ArrayList<TypeResponse>();
			activityResponses.add(0, ActivityResponse.makeDefault());
			ActivityResponse presentActivityResponse = activityResponses.get(0);

			for (RecordroidPlatformEvent event : platformEvents) {
				int platformEventType = event.platformEventType;
				long responseTimeUS = event.responseTimeUS;

				// Handles activity response
				if (platformEventType == RecordroidPlatformEvent.PlatformEventType.ACTIVITY_LAUNCH_EVENT) {
					int hashedComponentName = event.priv;
					ActivityResponse targetActivity = null;
					for (ActivityResponse activityResponse : activityResponses) {
						if (activityResponse.hashedComponentName == hashedComponentName)
							targetActivity = activityResponse;
					}
					if (targetActivity == null) {
						targetActivity = ActivityResponse
								.make(hashedComponentName);
						activityResponses.add(targetActivity);
					}
					presentActivityResponse = targetActivity;
				}

				// Handles type response in activity
				TypeResponse targetTypeResponse = null;
				for (TypeResponse typeResponse : presentActivityResponse.typeResponses) {
					if (typeResponse.platformEventType == platformEventType)
						targetTypeResponse = typeResponse;
				}
				if (targetTypeResponse == null) {
					targetTypeResponse = TypeResponse.make(platformEventType);
					presentActivityResponse.typeResponses
							.add(targetTypeResponse);
				}
				targetTypeResponse.totalResponseTimeUS = targetTypeResponse.totalResponseTimeUS
						+ responseTimeUS;

				// Handlers global type response
				targetTypeResponse = null;
				for (TypeResponse typeResponse : globalTypeResponses) {
					if (typeResponse.platformEventType == platformEventType)
						targetTypeResponse = typeResponse;
				}
				if (targetTypeResponse == null) {
					targetTypeResponse = TypeResponse.make(platformEventType);
					globalTypeResponses.add(targetTypeResponse);
				}
				targetTypeResponse.totalResponseTimeUS = targetTypeResponse.totalResponseTimeUS
						+ responseTimeUS;
			}

			// Step 4. Write total response time by event type
			writeBuffer.write("[Total Response Time]\n");
			writeBuffer.write("Type\tResponse time(us)\n");
			for (TypeResponse typeResponse : globalTypeResponses) {
				writeBuffer.write(typeResponse.getTextForResponseFile() + "\n");
			}
			writeBuffer.write("\n");
			// Step 5. Write response time of activity
			writeBuffer.write("[Activity Response Time]\n");
			writeBuffer.write("Hashed Name\tType\tResponse time(us)\n");
			for (ActivityResponse activityResponse : activityResponses) {
				writeBuffer.write(activityResponse.getTextForResponseFile());
			}
			writeBuffer.write("\n");
			// Step 6. Write response event trace
			writeBuffer.write("[Response Event Trace]\n");
			writeBuffer
					.write("Timestamp(US)\tType\tPrivate 1\tPrivate 2\tResponse time(us)\n");
			for (RecordroidPlatformEvent e : platformEvents) {
				writeBuffer.write(e.toStringForResponseFile() + "\n");
			}
			writeBuffer.flush();
			writeBuffer.close();
		}

		class TimestampSort implements Comparator<RecordroidPlatformEvent> {
			public int compare(RecordroidPlatformEvent e1,
					RecordroidPlatformEvent e2) {
				return (e1.timestampUS > e2.timestampUS) ? 1 : 0;
			}
		}
	}
}

class ActivityResponse {
	public boolean isDefault;
	public int hashedComponentName; // component name's hash value
	public ArrayList<TypeResponse> typeResponses = new ArrayList<TypeResponse>();

	private ActivityResponse(boolean isDefault, int hashedComponentName) {
		this.isDefault = isDefault;
		this.hashedComponentName = hashedComponentName;
	}

	public static ActivityResponse makeDefault() {
		return new ActivityResponse(true, 0);
	}

	public static ActivityResponse make(int hashedComponentName) {
		return new ActivityResponse(false, hashedComponentName);
	}

	public String getTextForResponseFile() {
		String ret = "";
		for (TypeResponse typeResponse : typeResponses) {
			String thisLine = "";
			if (this.isDefault) {
				thisLine += "(none)";
			} else {
				thisLine += this.hashedComponentName;
			}
			thisLine += "\t" + typeResponse.getTextForResponseFile();
			ret += thisLine + "\n";
		}
		return ret;
	}
}

class TypeResponse {
	public int platformEventType;
	public long totalResponseTimeUS;

	private TypeResponse(int platformEventType, long totalResponseTimeUS) {
		this.platformEventType = platformEventType;
		this.totalResponseTimeUS = totalResponseTimeUS;
	}

	public static TypeResponse make(int platformEventType) {
		return new TypeResponse(platformEventType, 0);
	}

	public String getTextForResponseFile() {
		String platformEventTypeStr = RecordroidPlatformEvent.PlatformEventType.names[this.platformEventType];
		String ret = platformEventTypeStr + "\t" + totalResponseTimeUS;
		return ret;
	}
}