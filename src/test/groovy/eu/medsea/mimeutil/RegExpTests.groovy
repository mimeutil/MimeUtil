package eu.medsea.mimeutil

import java.io.File;

import spock.lang.Specification
import spock.lang.Unroll
import eu.medsea.mimeutil.MimeUtil2;

class RegExpTests extends Specification {
	// Folder containing pattern files
	String path = "src/test/resources/magic.mine_test_patterns/"
	
	// Run before the first feature method
	def setupSpec() {
	}
	
	// Run after the last feature method
	def cleanupSpec() {
	}
	
	@Unroll
	def "Testing (#file): '#comment'" () {
		setup:
			System.setProperty "magic-mime", path + file
			MimeUtil2 mimeUtil = new MimeUtil2()
			def detector = mimeUtil.registerMimeDetector "eu.medsea.mimeutil.detector.MagicMimeMimeDetector"
			
        expect:
			(mimeUtil.getMimeTypes(new File("src/test/resources/regexp.txt")) == ["text/x-MimeUtil-RegExp"] ) == pass
			
		cleanup:
			System.clearProperty("magic-mime")
			mimeUtil = null
			
        where:
	        file         | pass | comment
			"fail_1.txt" | false | "Test Failing: Not enough numbers in file."
	        "fail_2.txt" | false | "Test Failing: Offset cuts off the first number."
	        "fail_3.txt" | false | "Test Failing: Read-length too short (must be at least 9)."
	        "fail_4.txt" | false | "Test Failing: Read-length does not cover the matching part."
	        "pass_1.txt" | true | "Test regular expression without offset."
	        "pass_2.txt" | true | "Test regular expression with offset."
	        "pass_3.txt" | true | "Test regular expression with offset and read-length (1)."
	        "pass_4.txt" | true | "Test regular expression with offset and read-length (2)."
			
	}
	
}
