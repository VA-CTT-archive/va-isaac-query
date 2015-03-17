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
package org.ihtsdo.otf.query.implementation.clauses;

import java.io.IOException;
import java.util.EnumSet;
import org.ihtsdo.otf.query.implementation.ClauseComputeType;
import org.ihtsdo.otf.query.implementation.ClauseSemantic;
import org.ihtsdo.otf.query.implementation.LeafClause;
import org.ihtsdo.otf.query.implementation.Query;
import org.ihtsdo.otf.query.implementation.WhereClause;
import org.ihtsdo.otf.tcc.api.concept.ConceptVersionBI;
import org.ihtsdo.otf.tcc.api.contradiction.ContradictionException;
import org.ihtsdo.otf.tcc.api.coordinate.ViewCoordinate;
import org.ihtsdo.otf.tcc.api.nid.ConcurrentBitSet;
import org.ihtsdo.otf.tcc.api.nid.NativeIdSetBI;
import org.ihtsdo.otf.tcc.api.relationship.RelationshipVersionBI;
import org.ihtsdo.otf.tcc.api.spec.ConceptSpec;
import org.ihtsdo.otf.tcc.api.spec.ValidationException;
import org.ihtsdo.otf.tcc.api.store.Ts;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Allows the user to define a restriction on the destination set of a
 * relationship query. Also allows the user to specify subsumption on the
 * destination restriction and relType.
 *
 * @author dylangrald
 */
@XmlRootElement
@XmlAccessorType(value = XmlAccessType.NONE)
public class RelRestriction extends LeafClause {


    @XmlElement
    String relTypeKey;
    @XmlElement
    String destinationSpecKey;
    @XmlElement
    String viewCoordinateKey;
    @XmlElement
    String destinationSubsumptionKey;
    @XmlElement
    String relTypeSubsumptionKey;

    NativeIdSetBI destinationSet;
    NativeIdSetBI relTypeSet;
    

    public RelRestriction(Query enclosingQuery, String relTypeKey, String destinationSpecKey,
            String viewCoordinateKey, String destinationSubsumptionKey, String relTypeSubsumptionKey) {
        super(enclosingQuery);
        this.destinationSpecKey = destinationSpecKey;
        this.relTypeKey = relTypeKey;
        this.viewCoordinateKey = viewCoordinateKey;
        this.relTypeSubsumptionKey = relTypeSubsumptionKey;
        this.destinationSubsumptionKey = destinationSubsumptionKey;

    }
    protected RelRestriction() {
    }
    @Override
    public WhereClause getWhereClause() {
        WhereClause whereClause = new WhereClause();
        whereClause.setSemantic(ClauseSemantic.REL_RESTRICTION);
        whereClause.getLetKeys().add(relTypeKey);
        whereClause.getLetKeys().add(destinationSpecKey);
        whereClause.getLetKeys().add(viewCoordinateKey);
        whereClause.getLetKeys().add(destinationSubsumptionKey);
        whereClause.getLetKeys().add(relTypeSubsumptionKey);
        System.out.println("Where clause size: " + whereClause.getLetKeys().size());
        return whereClause;

    }

    @Override
    public EnumSet<ClauseComputeType> getComputePhases() {
        return PRE_ITERATION_AND_ITERATION;
    }

    @Override
    public NativeIdSetBI computePossibleComponents(NativeIdSetBI incomingPossibleComponents) throws IOException, ValidationException, ContradictionException {
System.out.println("Let declerations: " + enclosingQuery.getLetDeclarations());
        ViewCoordinate viewCoordinate = (ViewCoordinate) enclosingQuery.getLetDeclarations().get(viewCoordinateKey);
        ConceptSpec destinationSpec = (ConceptSpec) enclosingQuery.getLetDeclarations().get(destinationSpecKey);
        ConceptSpec relType = (ConceptSpec) enclosingQuery.getLetDeclarations().get(relTypeKey);
        Boolean relTypeSubsumption = (Boolean) enclosingQuery.getLetDeclarations().get(relTypeSubsumptionKey);
        Boolean destinationSubsumption = (Boolean) enclosingQuery.getLetDeclarations().get(destinationSubsumptionKey);

        //The default is to set relTypeSubsumption and destinationSubsumption to true.
        if (relTypeSubsumption == null) {
            relTypeSubsumption = true;
        }
        if (destinationSubsumption == null) {
            destinationSubsumption = true;
        }

        relTypeSet = new ConcurrentBitSet();
        relTypeSet.add(relType.getNid());
        if (relTypeSubsumption) {
            relTypeSet.or(Ts.get().isKindOfSet(relType.getNid(), viewCoordinate));
        }

        destinationSet = new ConcurrentBitSet();
        destinationSet.add(destinationSpec.getNid());
        if (destinationSubsumption) {
            destinationSet.or(Ts.get().isKindOfSet(destinationSpec.getNid(), viewCoordinate));
        }

        return incomingPossibleComponents;
    }

    @Override
    public void getQueryMatches(ConceptVersionBI conceptVersion) throws IOException, ContradictionException {
        //Nothing to do here...
        for (RelationshipVersionBI rel: conceptVersion.getRelationshipsOutgoingActive()) {
            if (relTypeSet.contains(rel.getTypeNid())) {
                if (destinationSet.contains(rel.getDestinationNid())) {
                    getResultsCache().add(conceptVersion.getNid());
                    return;
                }
            }
        }
    }
}
