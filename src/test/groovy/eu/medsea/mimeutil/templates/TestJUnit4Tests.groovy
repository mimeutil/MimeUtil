package eu.medsea.mimeutil.templates

import org.junit.Test
import static org.junit.Assert.assertEquals

/**
 * Unit tests using JUnit4 "@Test annotation"-style.
 * 
 * (http://stackoverflow.com/questions/2635839/junit-confusion-use-extend-testcase-or-test)
 */
class TestJUnit4Tests {
	@Test
	void additionIsWorking() {
		assertEquals 4, 2+2
	}
	
	@Test(expected=ArithmeticException)
	void divideByZero() {
		println 1/0
	}
}
