package br.com.radiolistener.Fingerprinting;

public class MatchResult {
	private int trid;
	private int score;	
	private boolean isError;
	private String message;
	private int length;
	
	public MatchResult(int trid, int score, int length, boolean isError, String message)
	{
		this.trid = trid;
		this.score = score;
		this.isError = isError;
		this.message = message;
		this.length = length;
	}
	
	public int getTrid()
	{
		return this.trid;
	}
	
	public int getScore()
	{
		return this.score;
	}
	
	public boolean isError()	
	{
		return this.isError;
	}
	
	public String getMessage()
	{
		return this.message;
	}
	
	public boolean isMatch()
	{
		return this.trid >= 0;
	}
	
	public int getLength()
	{
		return this.length;
	}
}
