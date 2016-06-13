package com.android.server.recordroid;

public class ControllerState {
	class Type {
		static public final int INITIAL = 0;
		static public final int CONNECTED = 1;
		static public final int DISCONNECTED = 2;
		static public final int WAITING_FOR_STATE_CHANGE = 3;
	}

	private int mType;

	public ControllerState(int type) {
		this.mType = type;
	}

	public void setType(int type) {
		this.mType = type;
	}

	public int getType() {
		return this.mType;
	}
}
