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

import React, { useRef, useEffect, useState } from "react";

import { IconButton, Typography } from "@material-ui/core";
import AttachFile from '@material-ui/icons/AttachFile';

export default function DragAndDrop(props) {
  const { accept, multifile, handleDrop, classes, error } = props;
  const [drag, setDrag] = useState(false);
  const [dragCounter, setDragCounter] = useState(0);

  const dropRef = useRef();
  const inputRef = useRef();

  let handleDrag = (e) => {
    e.preventDefault();
    e.stopPropagation();
  }
  let handleDragIn = (e) => {
    e.preventDefault();
    e.stopPropagation();
    let count = dragCounter + 1;
    setDragCounter(count);
    if (e.dataTransfer.items && e.dataTransfer.items.length > 0) {
      setDrag(true);
    }
  }
  let handleDragOut = (e) => {
    e.preventDefault();
    e.stopPropagation();
    let count = dragCounter - 1;
    setDragCounter(count);
    if (dragCounter === 0) {
      setDrag(false);
    }
  }
  let handleOnDrop = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setDrag(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      handleDrop(e.dataTransfer.files);
      e.dataTransfer.clearData();
      setDragCounter(0);
    }
  };
  let handleClick = (e) => {
    inputRef.current.click();
  };
  let onChangeFile = (e) => {
    e.preventDefault();
    e.stopPropagation();
    let chosenFiles = event.target.files;
    handleDrop(chosenFiles);
  };

  useEffect(() => {
    let div = dropRef.current;
    div.addEventListener('dragenter', handleDragIn);
    div.addEventListener('dragleave', handleDragOut);
    div.addEventListener('dragover', handleDrag);
    div.addEventListener('drop', handleOnDrop);

    return () => {
      let div = dropRef.current;
      div.removeEventListener('dragenter', handleDragIn);
      div.removeEventListener('dragleave', handleDragOut);
      div.removeEventListener('dragover', handleDrag);
      div.removeEventListener('drop', handleOnDrop);
    };
  });

  return (
    <div style={{display: 'inline-block', position: 'relative'}}
       onClick={handleClick.bind(this)}
       ref={dropRef}
    >
      <input id="file-input"
        type="file"
        accept={accept}
        name="*"
        multiple={multifile}
        ref={inputRef}
        style={{display: 'none'}}
        onChange={onChangeFile.bind(this)}
      />
      <div className={drag ? classes.active : classes.dropzone} >
          <IconButton color="primary" component="span">
            <AttachFile />
          </IconButton>
          { error && <Typography color='error'>{error}</Typography> }
          { !error && <Typography>Drag & drop or browse files for upload</Typography> }
      </div>
    </div>
  )
}
