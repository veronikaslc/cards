//
//  Licensed to the Apache Software Foundation (ASF) under one
//  or more contributor license agreements.  See the NOTICE file
//  distributed with this work for additional information
//  regarding copyright ownership.  The ASF licenses this file
//  to you under the Apache License, Version 2.0 (the
//  "License"); you may not use this file except in compliance
//  with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing,
//  software distributed under the License is distributed on an
//  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
//  KIND, either express or implied.  See the License for the
//  specific language governing permissions and limitations
//  under the License.
//
import React, { useState, useEffect, useContext }  from 'react';
import { v4 as uuidv4 } from 'uuid';

import {
  Grid,
  Typography,
  makeStyles
} from '@material-ui/core';
import {
    Button,
    FormControl,
    IconButton,
    Input,
    InputLabel,
    Tooltip,
} from '@material-ui/core';

import { fetchWithReLogin, GlobalLoginContext } from "./login/loginDialogue.js";

const TEST_PATIENTS = [{
    last_name: "Addison",
    first_name: "John",
    date_of_birth: "1970-01-01",
    mrn: "1234567",
    health_card: "1234567890AB",
    visit_number: "11"
  }, {
    last_name: "Bennet",
    first_name: "Mary",
    date_of_birth: "1971-02-27",
    mrn: "2345678",
    health_card: "2345678901CD",
    visit_number: "22"
  }, {
    last_name: "Coleman",
    first_name: "Paul",
    date_of_birth: "1972-03-31",
    mrn: "3456789",
    health_card: "3456789012EF",
    visit_number: "33"
  }];

const useStyles = makeStyles(theme => ({
  form : {
    margin: theme.spacing(6, 3, 3),
  },
  logo : {
    maxWidth: "240px",
  },
  instructions : {
    textAlign: "center",
  },
}));

function MockPatientIdentification(props) {
  const { onSuccess } = props;

  const [ dob, setDob ] = useState();
  const [ mrn, setMrn ] = useState();
  const [ hc, setHc ] = useState();
  const [ error, setError ] = useState();
  const [ idData, setIdData ] = useState();
  const [ patient, setPatient ] = useState();
  const [ visit, setVisit ] = useState();

  const [ subjectTypes, setSubjectTypes ] = useState();

  const classes = useStyles();

  const globalLoginDisplay = useContext(GlobalLoginContext);

  // At startup, load subjectTypes
  useEffect(() => {
    fetchWithReLogin(globalLoginDisplay, "/query?query=" + encodeURIComponent("SELECT * FROM [cards:SubjectType]"))
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        let types = {};
        json.rows.forEach(r => types[r['@name']] = r['jcr:uuid']);
        setSubjectTypes(types);
      })
      .catch((response) => {
        setError(`Subject type retrieval failed with error code ${response.status}: ${response.statusText}`);
      });
  }, []);

  const sanitizeHC = (str) => {
    return str?.toUpperCase().replaceAll(/[^A-Z0-9]*/g, "") || "";
  }

  // TO DO: replace mock with authentication call
  const identify = () => {
    return TEST_PATIENTS.filter(p => (p.date_of_birth == dob && (!mrn || p.mrn == mrn) && (!hc || p.health_card == hc)))[0];
  }

  // On submitting the patient login form, make a request to identify the patient
  // If identification is successful, store the returned identification data (idData)
  const onSubmit = (event) => {
    event?.preventDefault();
    if (!dob || !mrn && !hc) {
      setError("Date of birth and either MRN or Health Card Number are required for patient identification");
      return;
    }
    setError("");
    let data = identify();
    if (data) {
      setIdData(data);
    } else {
      setError("No records match the submitted information");
    }
  }

  // When the identification data is successfuly obtained, get the patient subject's path
  useEffect(() => {
    idData && getPatient();
  }, [idData]);

  const getPatient = () => {
    getSubject(
      "Patient",   /* subject type */
      "/Subjects", /* path prefix*/
      idData.mrn,  /* id */
      'MRN',       /* id label */
      setPatient   /* successCallback */
    );
  }

  // When the patient subject path is successfully obtained, get the visit "subject"
  useEffect(() => {
    patient && getVisit();
  }, [patient]);

  const getVisit = () => {
    getSubject(
      "Visit",              /* subject type */
      patient,              /* path prefix*/
      idData.visit_number,  /* id */
      'visit number',       /* id label */
      setVisit              /* successCallback */
    );
  }

  // When the visit is successfully obtained, pass it along with the identification data
  // to the parent component
  useEffect(() => {
    visit && onSuccess && onSuccess(Object.assign({subject: visit}, idData));
  }, [visit]);


  // Get the path of a subject with a specific identifier
  // if the subject doesn't exist, create it
  const getSubject = (subjectType, pathPrefix, subjectId, subjectIdLabel, successCallback) => {
    // If the patient doesn't yet have an MRN, or the visit doesn't yet have a number, abort mission
    // Todo: after we find out if the MRN is not always assigned in the DHP,
    // in which case implement a different logic for finding the patient
    if (!subjectId) {
      setError(`The record was found but no ${subjectIdLabel} has been assigned yet. Please try again later or contact your care team for next steps.`);
      return;
    }

    // Look for the subject identified by subjectId
    let query=`SELECT * FROM [cards:Subject] as s WHERE ischildnode(s, "${pathPrefix}") AND s.identifier="${subjectId}"`;
    fetchWithReLogin(globalLoginDisplay, "/query?query=" + encodeURIComponent(query))
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
         let results = json.rows;
         if (results.length == 0) {
           // Subject not found: create it
           createSubject(subjectType, pathPrefix, subjectId, successCallback);
         } else if (results.length == 1) {
           // Subject found: return its path
           successCallback(results[0]['@path']);
         } else {
           // More than one subject found, not sure which one to pick: display error
           // Note: This should never actually happen
           setError("More than one matching record found. Please inform the technical administrator.");
         }
      })
      .catch((response) => {
        setError(`Record identification failed with error code ${response.status}: ${response.statusText}`);
      });
  }

  // Create a new subject if it's the first time we receive this identifier
  const createSubject = (type, path, id, successCallback) => {
    // Make a POST request to create a new subject
    let requestData = new FormData();
    requestData.append('jcr:primaryType', 'cards:Subject');
    requestData.append('identifier', id);
    requestData.append('type', subjectTypes[type]);
    requestData.append('type@TypeHint', 'Reference');

    let subjectPath = `${path}/` + uuidv4();
    fetchWithReLogin(globalLoginDisplay, subjectPath, { method: 'POST', body: requestData })
      .then((response) => response.ok ? successCallback(subjectPath) : Promise.reject(response))
      .catch((response) => {
        setError(`Data recording failed with error code ${response.status}: ${response.statusText}`);
      });
  }

  // -----------------------------------------------------------------------------------------------------
  // Rendering

  if (!subjectTypes) {
    return null;
  }

  return (
    <form className={classes.form} onSubmit={onSubmit} >
      <Grid container direction="column" spacing={4} alignItems="center" justify="center">
         <Grid item xs={12}>
           <img src="/libs/cards/resources/logo_light_bg.png" className={classes.logo} alt="logo" />
         </Grid>
         <Grid item xs={12} className={classes.instructions}>
         { error ?
           <Typography color="error">{error}</Typography>
           :
           <Typography>Please enter the following information for identification</Typography>
         }
         </Grid>
         <Grid item xs={12}>
            <FormControl margin="normal" required fullWidth>
              <InputLabel htmlFor="j_dob">Date of birth</InputLabel>
              <Input id="j_dob" name="j_dob" autoComplete="off" type="date" autoFocus onChange={event => setDob(event.target.value)}/>
            </FormControl>
            <Grid container direction="row" alignItems="flex-end" spacing={3} wrap="nowrap">
              <Grid item>
                <FormControl margin="normal" fullWidth>
                  <InputLabel htmlFor="j_mrn">MRN</InputLabel>
                  <Input id="j_mrn" name="j_mrn" autoComplete="off" type="number" onChange={event => setMrn(event.target.value)}/>
                 </FormControl>
              </Grid>
              <Grid item>or</Grid>
              <Grid item>
                <FormControl margin="normal" fullWidth>
                  <InputLabel htmlFor="j_hc">Health card number</InputLabel>
                  <Input id="j_hc" name="j_hc" autoComplete="off" onChange={event => setHc(sanitizeHC(event.target.value))}/>
                 </FormControl>
              </Grid>
            </Grid>
          </Grid>
          <Grid item>
            <Button
              type="submit"
              variant="contained"
              color="primary"
              className={classes.submit}
              >
              Submit
            </Button>
          </Grid>
       </Grid>
    </form>
  )
}

export default MockPatientIdentification;
