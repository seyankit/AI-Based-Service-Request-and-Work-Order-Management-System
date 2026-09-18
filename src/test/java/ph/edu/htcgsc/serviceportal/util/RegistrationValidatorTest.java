package ph.edu.htcgsc.serviceportal.util;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RegistrationValidatorTest {
 @Test void acceptsValidHtcEmail(){assertTrue(RegistrationValidator.validEmail("user@online.htcgsc.edu.ph"));}
 @Test void rejectsExternalEmail(){assertFalse(RegistrationValidator.validEmail("user@gmail.com"));}
 @Test void validatesCompositeNames(){assertTrue(RegistrationValidator.validRequiredName("Sean Keith"));assertTrue(RegistrationValidator.validOptionalName("Dela Cruz"));assertTrue(RegistrationValidator.validSuffix("Jr."));}
 @Test void validatesStrongPassword(){assertTrue(RegistrationValidator.strongPassword("Strong#123"));assertFalse(RegistrationValidator.strongPassword("weakpass"));}
 @Test void validatesPhilippineMobile(){assertTrue(RegistrationValidator.validContact("09171234567"));assertTrue(RegistrationValidator.validContact("+639171234567"));}
}
