package de.unikoblenz.west.koldfish.dam.test.simpl;

import static org.junit.Assert.fail;

import org.apache.jena.iri.IRI;
import org.apache.jena.iri.IRIFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import de.unikoblenz.west.koldfish.dam.Controller;
import de.unikoblenz.west.koldfish.dam.Negotiator;
import de.unikoblenz.west.koldfish.dam.Receiver;
import de.unikoblenz.west.koldfish.dam.except.ControllerException;
import de.unikoblenz.west.koldfish.dam.except.NegotiatorException;
import de.unikoblenz.west.koldfish.dam.impl.SimpleController;
import de.unikoblenz.west.koldfish.dam.impl.SimpleNegotiator;
import de.unikoblenz.west.koldfish.dam.messages.ReportMessage;

public class SimpleTest {

	private static final Logger log = LogManager.getLogger(SimpleTest.class);

	/**
	 * defines a simple test 
	 * @throws Throwable
	 */
	@Test
	public void testSimple() {
		log.debug("start");
				
		// initialize negotiator
		Negotiator<Model> neg = new SimpleNegotiator();
		
		// add simple Receiver
		neg.addReceiver(new Receiver() {
			@Override
			public void report(ReportMessage<?> rm) {
				if(rm.getPayload() instanceof Exception) {
					log.error("REPORT: " + rm.toString());
				}
				else {
					log.debug("REPORT: " + rm.toString());
				}
				
			}
		});
		
		// starting negotiator
		try {
			neg.start();
		} catch (NegotiatorException e) {
			log.error(e.toString(), e);
			fail();
		}
		
		// creating a controller
		Controller<Model> c = new SimpleController(neg);

		// create IRI to dereference
		IRI iri = IRIFactory.iriImplementation().construct("http://dbpedia.org/resource/Koblenz");
		
		// dereference IRI.
		try {
			log.debug("model size: " + c.deref(iri).size());
		} catch (ControllerException e) {
			log.error(e.toString(), e);
			fail();
		}
		
		// shutting negotiator down
		try {
			neg.terminate();
		} catch (NegotiatorException e) {
			log.error(e.toString(), e);
			fail();
		}
		
		log.debug("end");
	}

}
