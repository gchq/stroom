/*
 * Copyright 2016 Crown Copyright
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

package stroom.util.spring;

import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Component
public class BeanListFactory {
    @Resource
    private StroomBeanStore stroomBeanStore;

    private List<String> beanList;

    public void setBeanList(List<String> beanList) {
        this.beanList = beanList;
    }

    public List<Object> create() {
        List<Object> list = new ArrayList<>();
        if (beanList != null && stroomBeanStore != null) {
            for (String name : beanList) {
                list.add(stroomBeanStore.getBean(name));
            }
        }
        return list;
    }

}
