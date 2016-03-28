package br.com.radiolistener.Fingerprinting;

import java.util.List;

public interface IFingerprinter {

	MatchResult bestMatchForQuery(String code_string);
	void ingest(String code, int trid, int length);
	List<Integer> getLoadedTracks();
	
	
}
