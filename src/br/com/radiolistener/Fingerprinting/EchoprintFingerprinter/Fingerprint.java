package br.com.radiolistener.Fingerprinting.EchoprintFingerprinter;

public class Fingerprint {
	public int[] codes;
	public int[] times;
	
	public String toString()
	{
		StringBuilder builder = new StringBuilder();
		if(codes != null && times != null)
		{
			for(int i = 0; i < codes.length; i ++)
			{
				builder.append(codes[i]);
				builder.append(" ");
				builder.append(times[i]);
				builder.append(" ");
			}
			
			
			return builder.toString().trim();
		}else
		{
			return "Empty Fingerprint";
		}
		
		
	}
}
