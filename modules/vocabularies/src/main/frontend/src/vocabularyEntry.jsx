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
  Dialog,
  DialogContent,
  DialogTitle,
  IconButton,
  makeStyles,
  TableCell,
  TableRow,
  Typography,
  withStyles
} from "@material-ui/core";

import CloseIcon from "@material-ui/icons/Close";

import VocabularyDetails from "./vocabularyDetails"
import VocabularyAction from "./vocabularyAction"

const Config = require("./config.json");
const vocabLinks = require('./vocabularyLinks.json');
const Phase = require("./phaseCodes.json");

const useStyles = makeStyles(theme => ({
  closeButton: {
    position: 'absolute',
    right: theme.spacing(1),
    top: theme.spacing(1),
    color: theme.palette.grey[500]
  },
  title: {
    marginRight: theme.spacing(5)
  }
}));

const StyledTableRow = withStyles(theme => ({
  root: {
    "&:nth-of-type(odd)": {
      backgroundColor: theme.palette.background.default
    }
  },
}))(TableRow);

const StyledTableCell = withStyles(theme => ({
  body: {
    whiteSpace: "pre",
    textAlign: "right"
  },
}))(TableCell);

/*
  This function keeps track of the state of the current vocabulary. It also keeps track of any error messages needed to be displayed.
  Then it renders an action button and an about button. It also renders a dialog box for any installation / uninstallation errors
*/
export default function VocabularyEntry(props) {
  const { vocabulary, updateLocalList, hidden, initPhase } = props;
  // The following facilitates the usage of the same code to report errors for both installation and uninstallation
  const [error, setError] = React.useState(false);
  const [action, setAction] = React.useState("");
  const [errorMessage, setErrorMessage] = React.useState("");

  const [phase, setPhase] = React.useState(initPhase);

  const classes = useStyles();
  const date = new Date(vocabulary.installed || vocabulary.released);
  const bodyTypography = Config["tableBodyTypography"];

  const handleClose = () => {setError(false)};

  function install() {
    const oldPhase = phase;
    var badResponse = false;
    setPhase(Phase["Installing"]);
    props.setPhase(Phase["Installing"]);

    fetch(
      vocabLinks["install"]["base"] + "&identifier=" + vocabulary.acronym +
      Object.keys(vocabLinks["install"]["params"]).map(
        key => ("&" + key + "=" + vocabLinks["install"]["params"][key])
      ).join(""),
      {method: "POST"}
    )
    .then((resp) => resp.json())
    .then((resp) => {
      if(!resp["isSuccessful"]) {
        props.setPhase(oldPhase);
        setAction("Install");
        setErrorMessage(resp["error"]);
        setError(true);
        badResponse = true;
      }
    })
    .catch(function(error) {
      setPhase(oldPhase);
      props.setPhase(oldPhase);
      setAction("Install");
      setErrorMessage(error);
      setError(true);
      badResponse = true;
    })
    .finally(function() {
      if (!badResponse) {
        props.setPhase(Phase["Latest"]);
        updateLocalList("add", vocabulary);
      }
    });
  }

  function uninstall() {
    const oldPhase = phase;
    var badResponse = false;
    props.setPhase(Phase["Uninstalling"]);

    fetch(vocabLinks["uninstall"]["base"] + vocabulary.acronym, {method: "DELETE"})
    .then((resp) => {
      const code = resp.status;
      if(Math.floor(code/100) !== 2) {
        props.setPhase(oldPhase);
        setAction("Uninstall");
        setErrorMessage("Error " + code + ": " + resp.statusText);
        setError(true);
        badResponse = true;
        return Promise.reject(resp);
      }
    })
    .catch(function(error) {
      props.setPhase(oldPhase);
      setAction("Uninstall");
      setErrorMessage(error);
      setError(true);
      badResponse = true;
    })
    .finally(function() {
      if(!badResponse) {
        props.setPhase(Phase["Not Installed"]);
        updateLocalList("remove", vocabulary);
      }
    });
  }
  React.useEffect(() => {props.addSetter(setPhase);},[0]);

  return(
    <React.Fragment>
      {(!hidden) && (
      <React.Fragment>
        <StyledTableRow>

          <TableCell component="th" scope="row" >
            <Typography variant={bodyTypography}>
              {vocabulary.acronym}
            </Typography>
          </TableCell>

          <TableCell>
            <Typography variant={bodyTypography}>
              {vocabulary.name}
            </Typography>
          </TableCell>

          <TableCell>
            <Typography variant={bodyTypography} noWrap>
              {vocabulary.version}
            </Typography>
          </TableCell>

          <TableCell>
            <Typography variant={bodyTypography}>
              {date.toString().substring(4,15)}
            </Typography>
          </TableCell>

          <StyledTableCell>
            <VocabularyAction
              acronym={vocabulary.acronym}
              install={install}
              uninstall={uninstall}
              phase={phase}
            />
            { vocabulary.description &&
              <VocabularyDetails
                acronym={vocabulary.acronym}
                install={install}
                uninstall={uninstall}
                phase={phase}
                name={vocabulary.name}
                description={vocabulary.description}
              />
            }
          </StyledTableCell>

        </StyledTableRow>

        <Dialog open={error} onClose={handleClose}>

          <DialogTitle disableTypography>
            <Typography variant="h6" color="error" className={classes.title}>Failed to {action}</Typography>
            <IconButton onClick={handleClose} className={classes.closeButton}>
              <CloseIcon />
            </IconButton>
          </DialogTitle>

          <DialogContent dividers>
            <Typography variant="h6">{vocabulary.name}</Typography>
            <Typography variant="subtitle2" gutterBottom>Version: {vocabulary.version}</Typography>
            <Typography paragraph color="error">{errorMessage}</Typography>
          </DialogContent>

        </Dialog>

      </React.Fragment>
      )}
    </React.Fragment>
  );
}
