package stroom.job.impl.db;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.job.api.Job;
import stroom.job.impl.db.stroom.tables.records.JobRecord;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.junit.Assert.fail;

public class JobMappingTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobMappingTest.class);
    @Test
    public void testJob(){
        checkRecordAndDaoAlignmentWithReflection(JobRecord.class, Job.class, new String[]{"toString", "equals", "hashCode", "isAdvanced", "setAdvanced", "wait"});

//        checkRecordAndDaoAlignment();
    }

// This tests that the entity's properties exist in the record, but not vice versa.
    @Test
    public void checkRecordAndDaoAlignment(Class entityType, Class recordType) throws InvocationTargetException, IllegalAccessException {
        //TODO generate random instance of entityType
        Job entity = new Job();

        JobRecord record = new JobRecord();
        record.from(entity);

        Job mappedEntity = record.into(Job.class);

        List<Method> entityMethods = List.of(Job.class.getMethods());
        for(Method entityMethod : entityMethods){
            if(entityMethod.getParameterTypes().length == 0) {
                LOGGER.info("Comparing for method {}", entityMethod.getName());
                Object entityValue = entityMethod.invoke(entity);
                Object mappedValue = entityMethod.invoke(mappedEntity);
                LOGGER.info("Entity value is {} and mapped value is {}", entityValue, mappedValue);
                assertThat(entityValue).isEqualTo(mappedValue);
            }
        }
        //TODO reflective deep compare, excluding certain props
//        assertThat(mappedEntity.getDescription()).isEqualTo(entity.getDescription());
//        assertThat(mappedEntity.isEnabled()).isEqualTo(entity.isEnabled());
//        assertThat(mappedEntity.getId()).isEqualTo(entity.getId());
//        assertThat(mappedEntity.getVersion()).isEqualTo(entity.getVersion());
        //assertThat(mappedJob.isAdvanced()).isEqualTo(job.isAdvanced());

    }

    @Test
    public void checkRecordAndDaoAlignmentManually(){
        Job job = new Job();
        job.setDescription("sdfsd");
        job.setEnabled(true);
        job.setId(22);
        job.setVersion(3);
        job.setAdvanced(true);

        JobRecord jobRecord = new JobRecord();
        jobRecord.from(job);

        Job mappedJob = jobRecord.into(Job.class);

        assertThat(mappedJob.getDescription()).isEqualTo(job.getDescription());
        assertThat(mappedJob.isEnabled()).isEqualTo(job.isEnabled());
        assertThat(mappedJob.getId()).isEqualTo(job.getId());
        assertThat(mappedJob.getVersion()).isEqualTo(job.getVersion());
        //assertThat(mappedJob.isAdvanced()).isEqualTo(job.isAdvanced());


    }

    public void checkRecordAndDaoAlignmentWithReflection(Class recordType, Class entityType, String[] entityMethodsToIgnore){
        List<Method> recordMethods = List.of(recordType.getMethods());
        List<Method> entityMethods = List.of(entityType.getMethods());


        for(Method entityMethod : entityMethods){
            LOGGER.info("Checking entity method {}", entityMethod.getName());
            if(!List.of(entityMethodsToIgnore).contains(entityMethod.getName())){
                LOGGER.info("Including entity method {}", entityMethod.getName());
                List<Method> matchingMethods = recordMethods.stream()
                        .filter(method -> {
                            //LOGGER.info("Matching against record method {}", method.getName());
                            return method.getName().equals(entityMethod.getName());
                        })
                        .collect(Collectors.toList());

                if(matchingMethods.size() == 0){
                    LOGGER.info("Couldn't match method {}", entityMethod.getName());
                    fail();
                }else if(matchingMethods.size() == 1){
                    LOGGER.info("Matched method!");
                }else {
                    StringBuilder sb = new StringBuilder();
                    matchingMethods.forEach(sb::append);
                    LOGGER.info("Matched too many methods: {}", sb.toString());
                    fail("Matched too many methods!");
                }
            }
        }

    }
}
