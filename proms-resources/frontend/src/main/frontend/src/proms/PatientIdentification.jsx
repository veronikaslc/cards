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
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  FormControl,
  FormHelperText,
  Grid,
  IconButton,
  Input,
  InputLabel,
  Link,
  Typography,
  makeStyles
} from '@material-ui/core';
import CloseIcon from '@material-ui/icons/Close';

import ToUDialog from "./ToUDialog.jsx";

import DropdownsDatePicker from "../components/DropdownsDatePicker.jsx";

const useStyles = makeStyles(theme => ({
  form : {
    maxWidth: "500px",
    margin: "auto",
    padding: theme.spacing(2),
  },
  logo : {
    maxWidth: "240px",
    "@media (max-height: 725px)" : {
      maxHeight: "70px",
    }
  },
  description : {
    "& > *" : {
      textAlign: "center",
      marginBottom: "16px",
      "@media (max-width: 400px)" : {
        fontSize: "x-small",
      }
    }
  },
  formFields : {
    marginTop: 0,
    width : "100%",
  },
  mrnInput : {
    '& input[type=number]': {
        '-moz-appearance': 'textfield'
    },
    '& input[type=number]::-webkit-outer-spin-button': {
        '-webkit-appearance': 'none',
        margin: 0
    },
    '& input[type=number]::-webkit-inner-spin-button': {
        '-webkit-appearance': 'none',
        margin: 0
    }
  },
  dateLabel : {
      paddingTop: theme.spacing(1),
  },
  identifierDivider : {
    marginTop: '35px',
  },
  identifierContainer : {
    alignItems: "start",
  },
  closeButton: {
    float: 'right',
  },
  mrnHelperImage: {
    maxWidth: '100%',
  },
  mrnHelperLink: {
    cursor: 'pointer',
  },
}));

// The patient is already authenticated via the token.
// This just makes sure that the correct person accessed the application.
function PatientIdentification(props) {
  // Callback for reporting successful authentication
  const { onSuccess } = props;

  // The values entered by the user
  const [ dob, setDob ] = useState();
  const [ mrn, setMrn ] = useState();
  const [ hc, setHc ] = useState();

  // Internal state
  // Holds an error message for display
  const [ error, setError ] = useState();
  // Returned from the server after successul validation of the authentication,
  // and will be returned back to the rest of the PROMS UI through the onSuccess callback
  const [ patientDetails, setPatientDetails ] = useState();
  const [ visit, setVisit ] = useState();
  // Whether the patient user has accepted the latest version of the Terms of Use
  const [ touAccepted, setTouAccepted ] = useState(false);
  // Whether the Terms of Use dialog can be displayed after patient identification
  const [ showTou, setShowTou ] = useState(false);
  // Info about each patient is stored in a Patient information form

  const [ mrnHelperOpen, setMrnHelperOpen ] = useState(false);

  const classes = useStyles();

  const sanitizeHC = (str) => {
    return str?.toUpperCase().replaceAll(/[^A-Z0-9]*/g, "") || "";
  }

  const identify = () => {
    if (!dob || !mrn && !hc) {
      return null;
    }
    let requestData = new FormData();
    requestData.append("date_of_birth", dob);
    requestData.append("mrn", mrn);
    requestData.append("health_card", hc);
    fetch("/Proms.validateCredentials", {
      "method": "POST",
      "body": requestData
      })
      .then((response) => response.json())
      .then((json) => {
        if (json.status != "success") {
          return Promise.reject(json.error);
        }
        setPatientDetails(json.patientInformation);
        setVisit(json.sessionSubject);
        setShowTou(true);
      })
      .catch((error) => {
        setError(error.statusText ? error.statusText : error);
      });
  }

  // On submitting the patient login form, make a request to identify the patient
  const onSubmit = (event) => {
    event?.preventDefault();
    if (!dob || !mrn && !hc) {
      setError("Date of birth and either MRN or Health Card Number are required for patient identification");
      return;
    }
    setError("");
    setPatientDetails(null);
    setVisit(null);
    identify();
  }

  // When the visit is successfully obtained and the latest version of Terms of Use accepted, pass it along with the identification data
  // to the parent component
  useEffect(() => {
    visit && touAccepted && onSuccess && onSuccess(Object.assign({subject: visit}, patientDetails));
  }, [visit, touAccepted]);

  // -----------------------------------------------------------------------------------------------------
  // Rendering

  let appName = document.querySelector('meta[name="title"]')?.content;

  return (<>
    <ToUDialog
      open={showTou}
      actionRequired={true}
      onClose={() => setShowTou(false)}
      onAccept={() => {
        setShowTou(false);
        setTouAccepted(true);
      }}
      onDecline={() => {
        setShowTou(false)
        setPatientDetails(null);
        setVisit(null);
      }}
    />
    <Dialog onClose={() => {setMrnHelperOpen(false)}} open={mrnHelperOpen}>
      <DialogTitle>
        Where can I find my MRN?
        <IconButton onClick={() => setMrnHelperOpen(false)} className={classes.closeButton}>
          <CloseIcon />
        </IconButton>
      </DialogTitle>
      <DialogContent>
        <Typography paragraph>
          1. Check the top right-hand corner of your Patient Itinerary.
        </Typography>
        <img src="/libs/cards/resources/mrn_helper_1.png" alt="MRN location within the Appointment Itinerary" className={classes.mrnHelperImage} />
        <Typography paragraph>
          2. Check your account page on the myUHN PatientPortal.
        </Typography>
        <img src="/libs/cards/resources/mrn_helper_2.png" alt="MRN location within the Patient Portal side bar" className={classes.mrnHelperImage} />
      </DialogContent>
    </Dialog>
    <form className={classes.form} onSubmit={onSubmit} >
      <Grid container direction="column" spacing={4} alignItems="center" justify="center">
         <Grid item xs={12}>
           <img src="/libs/cards/resources/logo_light_bg.png" className={classes.logo} alt="logo" />
         </Grid>
         <Grid item xs={12} className={classes.description}>
           <Typography variant="h6">
             Welcome to {appName}
           </Typography>
           <Typography paragraph>
             {appName} is designed to ask you the most important questions about your health and well being. Your responses will remain confidential and will help your provider determine how we can best help you.
           </Typography>
           <Typography paragraph>
             Completing the questionnaire is voluntary, so if you would rather not complete it, you do not have to.
           </Typography>
           <Typography paragraph>
             If routine service evaluations or research projects are undertaken, your responses may be analyzed in a completely anonymous way.
           </Typography>
         </Grid>
         <Grid item xs={12} className={classes.formFields}>
            <div className={classes.description}>
            { error ?
              <Typography color="error">{error}</Typography>
              :
              <Typography variant="h6">Enter the following information for identification</Typography>
            }
            </div>
            <InputLabel htmlFor="j_dob" shrink={true} className={classes.dateLabel}>Date of birth</InputLabel>
            <DropdownsDatePicker id="j_dob" name="j_dob" formatDate onDateChange={setDob} autoFocus fullWidth/>
            <Grid container direction="row" alignItems="flex-end" spacing={3} wrap="nowrap" justify="space-between" className={classes.identifierContainer}>
              <Grid item>
                <FormControl margin="normal" fullWidth>
                  <InputLabel htmlFor="j_mrn" shrink={true}>MRN</InputLabel>
                  <Input id="j_mrn" name="j_mrn" autoComplete="off" type="number" placeholder="1234567" className={classes.mrnInput} onChange={event => setMrn(event.target.value)}/>
                  <FormHelperText id="mrn_helper">
                  <Link
                    color="primary"
                    variant="caption"
                    onClick={() => {setMrnHelperOpen(true)}}
                    className={classes.mrnHelperLink}
                    >
                    Where can I find my MRN?
                    </Link>
                  </FormHelperText>
                 </FormControl>
              </Grid>
              <Grid item className={classes.identifierDivider}>or</Grid>
              <Grid item>
                <FormControl margin="normal" fullWidth>
                  <InputLabel htmlFor="j_hc" shrink={true}>Health card number</InputLabel>
                  <Input id="j_hc" name="j_hc" autoComplete="off" placeholder="2345 678 901 XY" onChange={event => setHc(sanitizeHC(event.target.value))}/>
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
  </>)
}

export default PatientIdentification;