package zservers.zslib.network;

import zservers.zlib.main.ZLib;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ZAcknowledge implements Serializable, Id {

	private static final long serialVersionUID = -4170439613906587907L;

    private static ZLib main = ZLib.getInstance();

	public static final int SUCCESS = 0;
	public static final int ERROR = 1;
	public static final int TIMEOUT = 2;

	private final Id obj;
	private final Long timestamp;

	public final Integer state;
	public final String message;
	public final String info;

	public ZAcknowledge(Id acknowledgeFor, String info, int state, String stateMessage) {

		if(state != 0 && state != 1) throw new IllegalArgumentException("State value is illegal");

		this.obj = acknowledgeFor;
		this.timestamp = System.currentTimeMillis();
		this.info = info;
		this.state = state;
		this.message = stateMessage;
	}

	public ZAcknowledge(ZCommand command, int state, String stateMessage) {

		this.obj = command;
		this.timestamp = System.currentTimeMillis();
		this.info = command.toString();
		this.state = state;
		this.message = stateMessage;
	}

	public boolean isAcknowledgeFor(Id obj) {

//        main.dbg("this: " + this.obj.toString(),
//                 "other:" + obj.toString());
        if(this.obj.getClass() == obj.getClass()) return this.obj.getID() == obj.getID();
        else return false;
	}

	public String toString() {

        return "ZAcknowledge {" + state + " >> " + message + "} #" +
                new SimpleDateFormat("HH:mm:ss"/*:SSS"*/).format(new Date(timestamp))
                + " [" + obj.getClass().getName() + "]" +
                 " INF:" + info;
	}

    @Override
    public boolean equals(Object o) {

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ZAcknowledge that = (ZAcknowledge) o;

        if (!info.equals(that.info)) return false;
        if (!message.equals(that.message)) return false;
        if (!obj.equals(that.obj)) return false;
        if (!state.equals(that.state)) return false;
        if (!timestamp.equals(that.timestamp)) return false;

        return true;
    }

    @Override
    public int hashCode() {

        int result = obj.hashCode();
        result = 31 * result + timestamp.hashCode();
        result = 31 * result + state.hashCode();
        result = 31 * result + message.hashCode();
        result = 31 * result + info.hashCode();

        return result;
    }

    @Override
    public long getID() {

        if(obj != null) return obj.getID();
        else throw new IllegalStateException("No Acknowledge Type set");
    }
}
