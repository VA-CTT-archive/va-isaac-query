/*
 * Copyright 2013 International Health Terminology Standards Development Organisation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ihtsdo.otf.query.integration.tests;

import java.io.IOException;

import org.ihtsdo.otf.query.implementation.ComponentCollectionTypes;
import org.ihtsdo.otf.query.implementation.ForSetSpecification;
import org.ihtsdo.otf.tcc.api.coordinate.ViewCoordinate;
import org.ihtsdo.otf.tcc.api.metadata.binding.Snomed;
import org.ihtsdo.otf.query.implementation.Clause;
import org.ihtsdo.otf.query.implementation.Query;

/**
 *
 * @author kec
 */
public class ExampleQuery extends Query {

    public ExampleQuery(ViewCoordinate viewCoordinate) {
        super(viewCoordinate);
    }
    @Override
    protected ForSetSpecification ForSetSpecification() {
        return new ForSetSpecification(ComponentCollectionTypes.ALL_CONCEPTS);
    }
    @Override
    public void Let() {
        let("allergic-asthma", Snomed.ALLERGIC_ASTHMA);
    }

    @Override
    public Clause Where() {
        return And(ConceptIsKindOf("allergic-asthma"));
    }
}