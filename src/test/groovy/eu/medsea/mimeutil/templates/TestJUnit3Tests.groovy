package eu.medsea.mimeutil.templates

import org.junit.Test
import groovy.util.GroovyTestCase
import static org.junit.Assert.assertEquals

/**
 * Unit test using JUnit3 "extends TestCase"-style.
 * + Name of test methods need to start with "test".
 * (http://stackoverflow.com/questions/2635839/junit-confusion-use-extend-testcase-or-test)
 */
class TestJUnit3Tests extends GroovyTestCase {

	void testAdditionIsWorking() {
		assertEquals 4, 2+2
	}
	
	// Test for exceptions not possible in JUnit 3?
}
