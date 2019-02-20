/*
 * IcyListener.
 * 
 * JavaZOOM : mp3spi@javazoom.net
 * 			  http://www.javazoom.net
 * 
 *-----------------------------------------------------------------------
 *   This program is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU Library General Public License as published
 *   by the Free Software Foundation; either version 2 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Library General Public License for more details.
 *
 *   You should have received a copy of the GNU Library General Public
 *   License along with this program; if not, write to the Free Software
 *   Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *----------------------------------------------------------------------
 */

package com.allantaborda.jmp3dec.tag;

/** This class (singleton) allow to be notified on shoutcast meta data while playing the stream (such as song title). */
public class IcyListener implements TagParseListener{
	private static IcyListener instance;
	private MP3Tag lastTag;
	private String streamTitle;
	private String streamUrl;

	private IcyListener(){}

	public static synchronized IcyListener getInstance(){
		if(instance == null) instance = new IcyListener();
		return instance;		
	}
	
	public void tagParsed(TagParseEvent tpe){		
		lastTag = tpe.getTag();
		String name = lastTag.getName();
		if(name != null){
			if(name.equalsIgnoreCase("streamtitle")) streamTitle = (String) lastTag.getValue();
			else if(name.equalsIgnoreCase("streamurl")) streamUrl = (String) lastTag.getValue();
		}
	}

	/**
	 * Gets the last tag.
	 * @return The last tag.
	 */
	public MP3Tag getLastTag(){
		return lastTag;
	}

	/**
	 * Sets the last tag.
	 * @param tag The last tag.
	 */
	public void setLastTag(MP3Tag tag){
		lastTag = tag;
	}

	/**
	 * Gets the stream title.
	 * @return The stream title.
	 */
	public String getStreamTitle(){
		return streamTitle;
	}

	/**
	 * SGets the stream title.
	 * @param stream The stream title.
	 */
	public void setStreamTitle(String stream){
		streamTitle = stream;
	}

	/**
	 * Gets the stream url.
	 * @return The stream url.
	 */
	public String getStreamUrl(){
		return streamUrl;
	}

	/**
	 * Sets the stream url.
	 * @param stream The stream url.
	 */
	public void setStreamUrl(String stream){
		streamUrl = stream;
	}

	/** Reset all properties. */
	public void reset(){
		lastTag = null;
		streamTitle = null;
		streamUrl = null;		
	}
}