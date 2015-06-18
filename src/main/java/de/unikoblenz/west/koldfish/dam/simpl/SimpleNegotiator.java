package de.unikoblenz.west.koldfish.dam.simpl;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.hp.hpl.jena.rdf.model.Model;
import com.rits.cloning.Cloner;

import de.unikoblenz.west.koldfish.dam.Negotiator;
import de.unikoblenz.west.koldfish.dam.Receiver;
import de.unikoblenz.west.koldfish.dam.except.AccessorException;
import de.unikoblenz.west.koldfish.dam.except.NegotiatorException;
import de.unikoblenz.west.koldfish.dam.messages.ActivationMessage;
import de.unikoblenz.west.koldfish.dam.messages.ReportMessage;
import de.unikoblenz.west.koldfish.dam.messages.RequestMessage;
import de.unikoblenz.west.koldfish.dam.simpl.messages.DereferenceActivationMessage;
import de.unikoblenz.west.koldfish.dam.simpl.messages.ExceptionReportMessage;
import de.unikoblenz.west.koldfish.dam.simpl.messages.ModelReportMessage;

/**
 * simple Negotiator that reports complete Model objects.
 * 
 * @author lkastler@uni-koblenz.de
 *
 */
public class SimpleNegotiator implements Negotiator<Model> {

	private static final Logger log = LoggerFactory.getLogger(SimpleNegotiator.class);

	private final ListeningExecutorService accessors = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
	private final ExecutorService reporter = Executors.newCachedThreadPool(); 
	
	/** receiver list */
	private final List<Receiver> receivers = Collections.synchronizedList(new LinkedList<Receiver>());
	
	/**
	 * creates a new SimpleNegotiator.
	 */
	public SimpleNegotiator() {
		log.debug("created");
	}

	@Override
	public void start() throws NegotiatorException {
		log.debug("started");
	}
	
	@Override
	public void shutdown() throws NegotiatorException {
		log.debug("shutting down");
		shutdownAccessors();
		shutdownReporters();
		log.debug("shut down");
	}
	
	private void shutdownAccessors() throws NegotiatorException {
		accessors.shutdown();

		try {
			if (!accessors.awaitTermination(60, TimeUnit.SECONDS)) {
				accessors.shutdownNow();
				if (!accessors.awaitTermination(60, TimeUnit.SECONDS)) {
					log.error("could not shut down properly");
				}
			}
		} catch (InterruptedException e) {
			accessors.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	private void shutdownReporters() throws NegotiatorException {
		reporter.shutdown();

		try {
			if (!reporter.awaitTermination(60, TimeUnit.SECONDS)) {
				reporter.shutdownNow();
				if (!reporter.awaitTermination(60, TimeUnit.SECONDS)) {
					log.error("could not shut down properly");
				}
			}
		} catch (InterruptedException e) {
			reporter.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}
	
	@Override
	public void addReceiver(Receiver r) {
		synchronized(receivers) {
			receivers.add(r);
		}
	}

	@Override
	public void removeReceiver(Receiver r) {
		synchronized(receivers) {
			receivers.remove(r);
		}
	}
	
	@Override
	public Future<Model> request(RequestMessage rm) throws NegotiatorException {
		if(rm == null) 
			throw new NegotiatorException("RequestMessage is empty", new NullPointerException());
		
		log.debug("requesting: " + rm);
		
		ActivationMessage am = new DereferenceActivationMessage(rm.getResourceIRI());
		
		// create HttpAccess.
		HttpAccessor a = new HttpAccessor();
		try {
			ListenableFuture<Model> f = accessors.submit(a.activate(am));

			// register callback for reporting to receivers
			Futures.addCallback(f, new FutureCallback<Model>() {

				@Override
				public void onSuccess(Model result) {
					ReportMessage<Model> report = new ModelReportMessage(rm.getResourceIRI(), result);
					report(report);
				}

				@Override
				public void onFailure(Throwable t) {
					ReportMessage<Throwable> tm = new ExceptionReportMessage(rm.getResourceIRI(), t);
					report(tm);
				}
			});

			return f;
		} catch (AccessorException e) {
			throw new NegotiatorException(e.toString(), e);
		}
	}
	
	/**
	 * reports given ReportMessage to all receivers.
	 * @param rm - ReportMessage to report.
	 */
	private void report(ReportMessage<?> rm) {
		Cloner clone = new Cloner();
		synchronized(receivers) {
			reporter.execute(new Reporter(clone.deepClone(rm), receivers));			
		}
		
	}

	@Override
	public String toString() {
		return "SimpleNegotiator []";
	}
	
	private class Reporter implements Runnable {
		private final List<Receiver> receivers;
		private final ReportMessage<?> rm;
		
		Reporter(final ReportMessage<?> rm, final List<Receiver> recvs) {
			receivers = new LinkedList<Receiver>(recvs);
			this.rm = rm;
		}

		@Override
		public void run() {
			for(Receiver r : receivers) {
				r.report(rm);
			}	
		}
		
		
	}
}
