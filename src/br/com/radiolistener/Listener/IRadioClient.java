package br.com.radiolistener.Listener;
import java.util.Date;


public interface IRadioClient extends Runnable{
	Radio getRadio();
	void setRadioClientCallback(IRadioClientCallback callback);
	void start();
	void startThread();
	void stop();	
	Date getLastTake();
	ReceiverState getState();
	int getTakeCount();
	int getInitCount();
	int getQueueSize();
	int getMatchErrorCount();
}
