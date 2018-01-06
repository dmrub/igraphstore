/*
 * This file is part of IGraphStore. It is subject to the license terms in
 * the LICENSE file found in the top-level directory of this distribution.
 * You may not use this file except in compliance with the License.
 */
package de.dfki.resc28.igraphstore.jena;

import de.dfki.resc28.igraphstore.util.ProxyConfigurator;
import org.apache.jena.query.DatasetAccessorFactory;
import org.apache.jena.rdf.model.Model;

import de.dfki.resc28.igraphstore.IGraphStore;

/**
 * @author resc01
 *
 * The FusekiGraphStore maintains an RDF-Dataset.
 *
 * An RDF-Dataset is a collection of named graphs and a background graph (also
 * called the default or unnamed graph).
 */
public class FusekiGraphStore implements IGraphStore {

    static {
        ProxyConfigurator.initHttpClient();
    }

    //================================================================================
    // Constructors
    //================================================================================
    public FusekiGraphStore(final String dataServerURI, final String sparqlServerURI) {
        fDataServerURI = dataServerURI;
        fSparqlServerURI = sparqlServerURI;
    }

    //================================================================================
    // CRUD-related methods for the default graph
    //================================================================================
    /**
     * Gets the default graph by name as a Jena Model from the RDF-Dataset.
     * @return
     */
    @Override
    public Model getDefaultGraph() {
        try {
            return DatasetAccessorFactory.createHTTP(fDataServerURI).getModel();
        } catch (Exception ex) {
            throw new RuntimeException("Could not get default graph at URI: " + fDataServerURI, ex);
        }
    }

    /**
     * Replaces the default graph by the given model.
     * @param model
     */
    @Override
    public void replaceDefaultGraph(final Model model) {
        try {
            DatasetAccessorFactory.createHTTP(fDataServerURI).putModel(model);
        } catch (Exception ex) {
            throw new RuntimeException("Could not replace default graph at URI: " + fDataServerURI, ex);
        }
    }

    /**
     * Adds the statements of the given model to the default graph in the
     * RDF-Dataset.
     * @param model
     */
    @Override
    public void addToDefaultGraph(Model model) {
        try {
            DatasetAccessorFactory.createHTTP(fDataServerURI).add(model);
        } catch (Exception ex) {
            throw new RuntimeException("Could not add model to default graph at URI: " + fDataServerURI, ex);
        }
    }

    /**
     * Clears the default graph
     */
    @Override
    public void clearDefaultGraph() {
        try {
            DatasetAccessorFactory.createHTTP(fDataServerURI).deleteDefault();
        } catch (Exception ex) {
            throw new RuntimeException("Could not clear default graph at URI: " + fDataServerURI, ex);
        }
    }

    //================================================================================
    // CRUD-related methods for named graphs
    //================================================================================
    /**
     * Checks if RDF-Dataset contains graph with given name.
     * @param graphURI
     */
    @Override
    public boolean containsNamedGraph(final String graphURI) {
        try {
            return DatasetAccessorFactory.createHTTP(fDataServerURI).containsModel(graphURI);
        } catch (Exception ex) {
            throw new RuntimeException("Could not check if named graph " + graphURI + " contained at URI: " + fDataServerURI, ex);
        }
    }

    /**
     * Gets a graph by name as a Jena Model from the RDF-Dataset. Defaults to
     * background graph as a Jena Model.
     * @param graphURI
     * @return
     */
    @Override
    public Model getNamedGraph(final String graphURI) {
        try {
            return DatasetAccessorFactory.createHTTP(fDataServerURI).getModel(graphURI);
        } catch (Exception ex) {
            throw new RuntimeException("Could not get named graph " + graphURI + " at URI: " + fDataServerURI, ex);
        }
    }

    /**
     * Deletes a graph by name from the RDF-dataset.
     * @param graphURI
     */
    @Override
    public void deleteNamedGraph(final String graphURI) {
        try {
            DatasetAccessorFactory.createHTTP(fDataServerURI).deleteModel(graphURI);
        } catch (Exception ex) {
            throw new RuntimeException("Could not delete default graph at URI: " + fDataServerURI, ex);
        }
    }

    /**
     * Replaces a named graph in the RDF-Dataset by the given model.
     * @param graphURI
     * @param model
     */
    @Override
    public void replaceNamedGraph(final String graphURI, final Model model) {
        try {
            DatasetAccessorFactory.createHTTP(fDataServerURI).putModel(graphURI, model);
        } catch (Exception ex) {
            throw new RuntimeException("Could not replace named graph " + graphURI + " at URI: " + fDataServerURI, ex);
        }
    }

    /**
     * Adds the statements of the given model to the named graph in the
     * RDF-Dataset.
     * @param graphURI
     * @param model
     */
    @Override
    public void addToNamedGraph(String graphURI, Model model) {
        try {
            DatasetAccessorFactory.createHTTP(fDataServerURI).add(graphURI, model);
        } catch (Exception ex) {
            throw new RuntimeException("Could not add model to named graph " + graphURI + " at URI: " + fDataServerURI, ex);
        }
    }

    /**
     * Creates a graph in the RDF-Dataset by name with the given model.
     * @param graphURI
     * @param model
     */
    @Override
    public void createNamedGraph(final String graphURI, final Model model) {
        try {
            DatasetAccessorFactory.createHTTP(fDataServerURI).putModel(graphURI, model);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create named graph " + graphURI + " at URI: " + fDataServerURI, ex);
        }
    }

    //================================================================================
    // Query-related Methods
    //================================================================================
    //================================================================================
    // Member variables
    //================================================================================
    private final String fDataServerURI;
    private final String fSparqlServerURI;
}
