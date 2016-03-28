package br.com.radiolistener.Listener;
import java.util.Date;

import javax.sql.DataSource;

import br.com.radiolistener.Fingerprinting.MatchResult;




public interface IRadioClientCallback {
	MatchResult onCode(Date date, Radio radio, String code);
	DataSource getDataSource();
}
