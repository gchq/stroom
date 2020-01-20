package stroom.config.global.impl.validation;

//import org.hibernate.validator.spi.nodenameprovider.JavaBeanProperty;
//import org.hibernate.validator.spi.nodenameprovider.Property;
//import org.hibernate.validator.spi.nodenameprovider.PropertyNodeNameProvider;

/**
 * Use the @JsonPropery name instead of the bean property name when producing constraint violations
 * This is a stop-gap until https://hibernate.atlassian.net/browse/HV-823 makes it into the version
 * of hibernate-validator that we are using.
 */
public class JacksonPropertyNodeNameProvider {


//        private final ObjectMapper objectMapper = new ObjectMapper();
//
//        @Override
//        public String getName(Property property) {
//            if ( property instanceof JavaBeanProperty) {
//                JavaBeanProperty javaBeanProperty = (JavaBeanProperty) property;
//                return getJavaBeanPropertyName( (JavaBeanProperty) property );
//            }
//
//            return getDefaultName( property );
//        }
//
//        private String getJavaBeanPropertyName(JavaBeanProperty property) {
//            JavaType type = objectMapper.constructType( property.getDeclaringClass() );
//            BeanDescription desc = objectMapper.getSerializationConfig().introspect( type );
//
//            return desc.findProperties()
//                .stream()
//                .filter( prop -> prop.getInternalName().equals( property.getName() ) )
//                .map( BeanPropertyDefinition::getName )
//                .findFirst()
//                .orElse( property.getName() );
//        }
//
//        private String getDefaultName(Property property) {
//            return property.getName();
//        }

}
