/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.pipeline.server.reader;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.util.spring.StroomScope;

@Configuration
public class ReaderSpringConfig {
    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public BOMRemovalFilterInputElement bOMRemovalFilterInputElement() {
        return new BOMRemovalFilterInputElement();
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public BadTextXMLFilterReaderElement badTextXMLFilterReaderElement(final ErrorReceiverProxy errorReceiver) {
        return new BadTextXMLFilterReaderElement(errorReceiver);
    }

//    @Bean
//    @Scope(StroomScope.PROTOTYPE)
//    public InputStreamRecordDetector inputStreamRecordDetector() {
//        return new InputStreamRecordDetector();
//    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public InputStreamRecordDetectorElement inputStreamRecordDetectorElement() {
        return new InputStreamRecordDetectorElement();
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public InvalidCharFilterReaderElement invalidCharFilterReaderElement(final ErrorReceiverProxy errorReceiver) {
        return new InvalidCharFilterReaderElement(errorReceiver);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public InvalidXMLCharFilterReaderElement invalidXMLCharFilterReaderElement(final ErrorReceiverProxy errorReceiver) {
        return new InvalidXMLCharFilterReaderElement(errorReceiver);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public ReaderElement readerElement() {
        return new ReaderElement();
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public ReaderRecordDetectorElement readerRecordDetectorElement() {
        return new ReaderRecordDetectorElement();
    }
}