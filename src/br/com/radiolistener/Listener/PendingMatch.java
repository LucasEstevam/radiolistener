package br.com.radiolistener.Listener;

import java.util.Date;

public class PendingMatch {

	private int idMatch;
	private int idRadio;
	private int trid;
	private Date startTime;
	private int trackDuration;
	private int matchDuration;
	
	public PendingMatch(int idMatch, int idRadio, int trid, Date startTime, int trackDuration, int matchDuration)
	{
		this.idMatch = idMatch;
		this.idRadio = idRadio;
		this.trid = trid;
		this.startTime = startTime;
		this.trackDuration = trackDuration;
		this.matchDuration = matchDuration;
	}
	
	public int getIdMatch(){
		return this.idMatch;
	}
	
	public int getIdRadio()
	{
		return this.idRadio;
	}
	
	public int getTrid()
	{
		return this.trid;
	}
	
	public Date getStartTime()
	{
		return this.startTime;
	}
	
	public int getTrackDuration()
	{
		return this.trackDuration;
	}
	
	public int getMatchDuration(){
		return this.matchDuration;
	}
	
	public void setMatchDuration(int value)
	{
		this.matchDuration = value;
	}
	
	
}
