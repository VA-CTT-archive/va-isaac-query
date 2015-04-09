package org.ihtsdo.otf.query.integration.tests;

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
import gov.vha.isaac.metadata.coordinates.ViewCoordinates;
import java.io.IOException;
import org.ihtsdo.otf.query.implementation.Clause;
import org.ihtsdo.otf.query.implementation.ComponentCollectionTypes;
import org.ihtsdo.otf.query.implementation.ForSetSpecification;
import org.ihtsdo.otf.query.implementation.Query;
import org.ihtsdo.otf.tcc.api.metadata.binding.Snomed;

/**
 * Creates a test for the {@link org.ihtsdo.otf.query.implementation.Or} clause.
 *
 * @author dylangrald
 */
public class OrTest extends QueryClauseTest {

    public OrTest() throws IOException {
        this.q = new Query(ViewCoordinates.getDevelopmentInferredLatestActiveOnly()) {
            @Override
            protected ForSetSpecification ForSetSpecification() {
                return new ForSetSpecification(ComponentCollectionTypes.ALL_CONCEPTS);
            }

            @Override
            public void Let() throws IOException {
                let("acceleration", Snomed.ACCELERATION);
                let("vibration", Snomed.VIBRATION);
            }

            @Override
            public Clause Where() {
                return Or(ConceptIsKindOf("acceleration"), ConceptIs("vibration"));
            }
        };
    }
}