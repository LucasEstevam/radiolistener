package br.com.radiolistener.Listener;
import java.io.IOException;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;


public class ManagerWebHandler extends AbstractHandler{
	
	private RadioClientManager manager;
	
	public ManagerWebHandler(RadioClientManager manager){
		this.manager = manager;
	}

	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request,
			HttpServletResponse response) throws IOException, ServletException {
		response.setContentType("text/html;charset=utf-8");
        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);
        Date now = new Date();
        for (RadioClient client:manager.getRadioClients().values())
        {
        	try{
        		String color = "blue";
        		if(client.getState() != ReceiverState.PLAYING)
        		{
        			color = "red";
        		}else if((now.getTime() - client.getLastTake().getTime()) > 60*1000)
        		{
        			if(client.getTakeCount() > 0)
        			{
        				color = "orange";
        			}else
        			{
        				color = "red";
        			}
        		}
        	        	response.getWriter().println("<font color=" + color + ">Id:" + client.getRadio().getId() + " Nome: " + client.getRadio().getNome() + " + Status:" + client.getState().toString() + " LastTake:" + client.getLastTake().toString() + " TakeCount: " + client.getTakeCount() + " InitCount: " + client.getInitCount() + " QueueSize: " + client.getQueueSize() + " MatchErrors: " + client.getMatchErrorCount() +  "</font><br>");
        	}catch(Exception ex)
        	{
        		response.getWriter().println(ex.getMessage());
        	}
        }
		
	}

}
