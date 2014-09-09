package eu.medsea.mimeutil.templates

import spock.lang.Specification
import spock.lang.Unroll

class TestSpockTests extends Specification {
	/* @Unroll leads to tests being listed in "Unrooted Tests" in Eclipse:
	 * http://stackoverflow.com/questions/22159793/how-do-i-stop-unrolled-spock-tests-from-showing-up-as-unrooted-tests-in-the-e
	 * https://bugs.eclipse.org/bugs/show_bug.cgi?id=429606
	 */
	@Unroll
	def "Feature Test: square of #a is #b"() {
        expect:
		a * a == b

        where:
        a | b
        1 | 1
        2 | 4
        3 | 9
        4 | 16
        5 | 25
        6 | 36
        7 | 49
        8 | 64
        9 | 81
    }
	
	def "TestMaximum"() {
		when:
		def x = Math.max(1, 2)
		
		then:
		x == 2
	}
	
	/*
	 * Spaces in the name of a test-method make it impossible in Eclipse
	 * to jump to the method by clicking on the name in the JUnit view.
	 * (https://bugs.eclipse.org/bugs/show_bug.cgi?id=443581)
	 */
	def "Test Minimum"() {
		when:
		def x = Math.min(1, 2)
		
		then:
		x == 1
	}
	
}
