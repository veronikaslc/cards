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

import React from "react";

import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  CircularProgress,
  makeStyles,
  Typography,
  Tooltip
} from "@material-ui/core";

const Phase = require("./phaseCodes.json");

const useStyles = makeStyles(theme => ({
  button: {
    margin: theme.spacing(1),
    textTransform: "none",
    color: "white"
  },
  buttonProgress: {
    top: "50%",
    left: "50%",
    position: "absolute",
    marginTop: -12,
    marginLeft: -12,
    textTransform: "none"
  },
  install: {
    background: "green",
    "&:hover": {
      background: "#075706"
    }
  },
  uninstall: {
    background: "red",
    "&:hover": {
      background: "#a3211f"
    }
  },
  installingColor: {
    color: "green",
  },
  uninstallingColor: {
    color: "red",
  },
  update: {
    background: "orange",
    "&:hover": {
      background: "#a36c1f"
    }
  },
  wrapper: {
    position: "relative",
    display: "inline-block"
  }
}));

export default function VocabularyAction(props) {
  const classes = useStyles();
  const [displayPopup, setDisplayPopup] = React.useState(false);
  const handleOpen = () => {setDisplayPopup(true);}
  const handleClose = () => {setDisplayPopup(false);}
  const handleUninstall = () => {setDisplayPopup(false); props.uninstall();}

  return(
    <React.Fragment>
    {(props.phase == Phase["Not Installed"]) && (
      <Tooltip title="Install this vocabulary">
        <Button onClick={props.install} variant="contained" className={classes.button + " " + classes.install}>Install</Button>
      </Tooltip>
    )}
    {(props.phase == Phase["Installing"]) && (
      <span className={classes.wrapper}>
        <Button disabled variant="contained" className={classes.button}>Installing</Button>
        <CircularProgress size={24} className={classes.buttonProgress + " " + classes.installingColor} />
      </span>
    )}
    {(props.phase == Phase["Update Available"]) && (
      <React.Fragment>
        <Tooltip title="Update this vocabulary">
          <Button onClick={props.install} variant="contained" className={classes.button + " " + classes.update}>Update</Button>
        </Tooltip>
        <Tooltip title="Remove this vocabulary">
          <Button onClick={props.uninstall} variant="contained" className={classes.button + " " + classes.uninstall}>Uninstall</Button>
        </Tooltip>
      </React.Fragment> 
    )}
    {(props.phase == Phase["Uninstalling"]) && (
      <span className={classes.wrapper}>
        <Button disabled variant="contained" className={classes.button}>Uninstalling</Button>
        <CircularProgress size={24} className={classes.buttonProgress + " " + classes.uninstallingColor} />
      </span>
    )}
    {(props.phase == Phase["Latest"]) && (
      <Tooltip title="Remove this vocabulary">
        <Button onClick={handleOpen} variant="contained" className={classes.button + " " + classes.uninstall}>Uninstall</Button>
      </Tooltip>
    )}
    <Dialog onClose={handleClose} open={displayPopup}>

      <DialogTitle disableTypography>
        <Typography variant="h4" className={classes.dialogTitle}>{props.acronym}</Typography>
      </DialogTitle>

      <DialogContent dividers>
        <Typography variant="h6">{props.name}</Typography>
        <Typography variant="body1">Uninstalling this vocabulary may result in data not being properly standardized. Proceed?</Typography>
      </DialogContent>

      <DialogActions>
        <Button onClick={handleUninstall} variant="contained" className={classes.button + " " + classes.uninstall}>Uninstall</Button>
        <Button onClick={handleClose} variant="contained" className={classes.button + " " + classes.exit}>Cancel</Button>
      </DialogActions>

    </Dialog>
    </React.Fragment>
  );
}
