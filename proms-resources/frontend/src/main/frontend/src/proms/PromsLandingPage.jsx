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

import React, { useState, useEffect } from "react";

import {
  Breadcrumbs,
  Button,
  CircularProgress,
  Dialog,
  DialogContent,
  Grid,
  Tooltip,
  Typography,
  makeStyles
} from '@material-ui/core';

import { MuiThemeProvider } from '@material-ui/core/styles';
import { appTheme } from "../themePalette.jsx";

const useStyles = makeStyles(theme => ({
  paper: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    height: '100%',
  },
  logo : {
    maxWidth: "200px",
    marginBottom: theme.spacing(5),
    marginTop: theme.spacing(9.5),
  },
  buttonContainer: {
    position: "relative",
    "& .MuiCircularProgress-root" : {
       position: 'absolute',
       top: '50%',
       left: '50%',
       marginTop: '-12px',
       marginLeft: '-12px',
    },
  },
  button : {
    textTransform: "none",
    minWidth: "250px",
    padding: theme.spacing(2, 1),
  },
  appInfo : {
    paddingTop: theme.spacing(.5),
    marginTop: theme.spacing(5),
  }
}));

function PromsLandingPage(props) {

  const classes = useStyles();

  const [ isOpen, setIsOpen ] = useState(true);
  const [ loadingPatientLogin, setLoadingPatientLogin ] = useState(false);
  const [ loadingHCPLogin, setLoadingHCPLogin ] = useState(false);

  const appInfo = document.querySelector('meta[name="title"]').content;

  const USER_TYPE_PARAM = "usertype";
  const USER_TYPE_HCP = "hcp";

  useEffect(() => {
    let userType = (new URLSearchParams(window.location.search || ""))?.get(USER_TYPE_PARAM);
    setIsOpen(!(userType == USER_TYPE_HCP));
  }, [window.location]);

  return (
    <Dialog
      fullScreen
      open={isOpen}
    >
      <MuiThemeProvider theme={appTheme}>
        <DialogContent className={classes.paper}>
          <Grid container direction="column" spacing={2} alignItems="center" alignContent="center">
            <Grid item>
              <img src="/libs/cards/resources/logo_light_bg.png" alt="" className={classes.logo} />
            </Grid>
            <Grid item>
              <Typography variant="h6">I am a...</Typography>
            </Grid>
            <Grid item>
              <Grid container spacing={3} direction="column" justify="center" alignItems="center">
                <Grid item className={classes.buttonContainer}>
                  <Button
                    fullWidth
                    variant="contained"
                    color="primary"
                    disabled={loadingPatientLogin}
                    className={classes.button}
                    onClick={() => {
                      setLoadingPatientLogin(true);
                      window.location = "/Proms";
                    }}
                   >
                    <Typography variant="h6">Patient</Typography>
                  </Button>
                  {loadingPatientLogin && <CircularProgress size={24} />}
                </Grid>
                <Grid item className={classes.buttonContainer}>
                  <Button
                    fullWidth
                    variant="contained"
                    disabled={loadingHCPLogin}
                    className={classes.button}
                    onClick={() => {
                      setLoadingHCPLogin(true);
                      let query = new URLSearchParams(window.location?.search || "");
                      query.delete(USER_TYPE_PARAM);
                      query.append(USER_TYPE_PARAM, USER_TYPE_HCP);
                      window.location.search = query;
                    }}
                   >
                    <Typography variant="h6">Health Care Provider</Typography>
                  </Button>
                  {loadingHCPLogin && <CircularProgress size={24} />}
                </Grid>
              </Grid>
            </Grid>
            <Grid item>
              <Breadcrumbs separator="by" className={classes.appInfo}>
                <Typography variant="subtitle2">{appInfo}</Typography>
                <Tooltip title="DATA Team @ UHN">
                  <a href="https://uhndata.io/" target="_blank">
                    <img src="/libs/cards/resources/data-logo_light_bg.png" width="80" alt="DATA" />
                  </a>
                </Tooltip>
              </Breadcrumbs>
            </Grid>
          </Grid>
        </DialogContent>
      </MuiThemeProvider>
    </Dialog>
  );
}

export default PromsLandingPage;
