# Collaborative Plain Text Editor

## Project Overview

A real-time collaborative plain text editor built in Java that allows multiple users to simultaneously edit the same document. Users can collaborate through shareable codes, with support for both editor and read-only access.

## Features

### Document & Collaboration Management:

- **File Operations**: Import and export text files with preserved formatting
- **Sharing System**: Generate unique sharing codes for each document
  - Editor access code for full editing privileges
  - Read-only access code for viewers

### Real-time Collaborative Editing

- **Live Editing**: Character-by-character insertions and deletions with instant updates
- **Conflict Resolution**: Implementation of a tree-based CRDT (Conflict-free Replicated Data Type) algorithm to handle concurrent edits
- **Real-time Synchronization**: Central server relays changes between all connected users
- **Cursor Tracking**: See other users' cursor positions with distinct colors
- **User Presence**: View which users are currently active in the session
- **Undo/Redo**: Support for undoing and redoing your own changes (minimum 3 operations)

### User Interface

- **Editing Area**: Clean text editor interface for document manipulation
- **Collaboration Controls**: Easy joining of existing sessions via codes
- **Permission Management**: Different interfaces for editors vs. viewers
- **Visual Indicators**: Color-coded cursors for different users (supports up to 4 concurrent editors)
- **Active User List**: See who is currently editing the document

### Advanced Features

- **User Comments**: Add contextual comments to specific parts of text
- **Reconnection Support**: 5-minute grace period for reconnection with synchronization of missed changes

## Technical Implementation

- Java-based application with client-server architecture
- CRDT algorithm for handling concurrent modifications
- Real-time network communication for collaborative features
- Permission-based access control system

## How It Works

1. Create or import a text document
2. Generate and share access codes with collaborators
3. Connect in real-time to see and make changes together
4. Export the final document when collaboration is complete
