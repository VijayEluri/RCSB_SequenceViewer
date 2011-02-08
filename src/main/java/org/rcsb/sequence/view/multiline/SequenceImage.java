package org.rcsb.sequence.view.multiline;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.biojava3.protmod.ProteinModification;
import org.biojava3.protmod.structure.ModifiedCompound;

import org.rcsb.sequence.conf.AnnotationName;
import org.rcsb.sequence.conf.AnnotationRegistry;
import org.rcsb.sequence.core.AnnotationDrawMapper;
import org.rcsb.sequence.core.ProtModAnnotationGroup;

import org.rcsb.sequence.model.ResidueNumberScheme;
import org.rcsb.sequence.model.SegmentedSequence;
import org.rcsb.sequence.model.Sequence;
import org.rcsb.sequence.util.MapOfCollections;

/**
 * <tt>SequenceImage</tt> is responsible for creating a png bitmap image for a given {@link Sequence},
 * {@link SegmentedSequence} or <tt>List&lt;Sequence&gt;</tt>.
 * 
 * @author mulvaney
 */
public class SequenceImage extends AbstractSequenceImage
{

	private boolean DEBUG = false;
	AnnotationDrawMapper annotationDrawMapper ;

	private ProtModDrawerUtil modDrawerUtil;

	/**
	 * Constructor for creating images of a {@link SegmentedSequence}. Each <tt>SequenceSegment</tt> in the
	 * <tt>SegmentedSequence</tt> is treated as an individual sequence and stacked.
	 * 
	 * @param sequence
	 * @param annotationsToView
	 * @param rnsOfBottomRuler
	 * @param rnsOfTopRuler
	 * @param fontSize
	 * @param fragmentBuffer
	 * @param numCharsInKey
	 */
	public SequenceImage(SegmentedSequence sequence, Collection<AnnotationName> annotationsToView, ResidueNumberScheme rnsOfBottomRuler,
			ResidueNumberScheme rnsOfTopRuler, int fontSize, float fragmentBuffer, int numCharsInKey,AnnotationDrawMapper annotationDrawMapper)
	{
		this(sequence.getSequenceSegments(), annotationsToView, rnsOfBottomRuler, rnsOfTopRuler, fontSize, fragmentBuffer, numCharsInKey,annotationDrawMapper);
	}

	/**
	 * Create a sequence image for a given {@link Sequence}. The whole sequence is rendered on one horizontal line
	 * without line breaks. If the <tt>Sequence</tt> is too long, consider requesting a {@link SegmentedSequence} from
	 * the <tt>Sequence</tt>
	 * 
	 * @param sequence
	 * @param annotationsToView
	 * @param rnsOfBottomRuler
	 * @param rnsOfTopRuler
	 * @param fontSize
	 * @param fragmentBuffer
	 * @param numCharsInKey
	 * @see Sequence#getSegmentedSequence(int, ResidueNumberScheme)
	 */
	public SequenceImage(Sequence sequence, Collection<AnnotationName> annotationsToView, ResidueNumberScheme rnsOfBottomRuler,
			ResidueNumberScheme rnsOfTopRuler, int fontSize, float fragmentBuffer, int numCharsInKey,AnnotationDrawMapper annotationDrawMapper)
	{
		this(Collections.singletonList(sequence), annotationsToView, rnsOfBottomRuler, rnsOfTopRuler, fontSize, fragmentBuffer, numCharsInKey,annotationDrawMapper);
	}

	public SequenceImage(SegmentedSequence sequence, Collection<AnnotationName> annotationsToView, ResidueNumberScheme rnsOfBottomRuler,
			int fontSize, float fragmentBuffer, int numCharsInKey,AnnotationDrawMapper annotationDrawMapper)
	{
		this(sequence, annotationsToView, rnsOfBottomRuler, null, fontSize, fragmentBuffer, numCharsInKey,annotationDrawMapper);
	}

	public SequenceImage(List<? extends Sequence> sequences, Collection<AnnotationName> annotationsToView,
			ResidueNumberScheme rnsOfBottomRuler, ResidueNumberScheme rnsOfTopRuler, int fontSize, float fragmentBuffer, int numCharsInKey, AnnotationDrawMapper annotationDrawMapper)
	{

		this.annotationDrawMapper = annotationDrawMapper;

		initImage(sequences, fontSize, fragmentBuffer, numCharsInKey);
//		
//		for ( AnnotationName name : annotationsToView){
//			System.out.println("SequenceImage viewing:  " + name.getName());	
//		}
		
		
		debug("SequneceImage has been asked to view annotations: " + annotationsToView);
		
		int yOffset = 0;
		SequenceDrawer sequenceDrawer = null;

		annotationDrawMapper.ensureInitialized();
		// this drawer does nothing except take up vertical space. it is
		// inserted between sequence segments to create a buffer between
		// them
		Drawer spacer = new SpacerDrawer(fragmentBufferPx);

		boolean ptmAnnotationExists = false;
		@SuppressWarnings("rawtypes")
		Class protModClass = null;

		for (Sequence s : sequences)
		{

			// add renders
			for (AnnotationName an : AnnotationRegistry.getAllAnnotations())
			{

				if (an.getName().equals("disulphide"))
					continue;
				if (an.getName().equals("Protein modification") && annotationsToView.contains(an)) {
					ptmAnnotationExists = true;
					protModClass = an.getAnnotationClass();
					debug("found implementing class:" + protModClass.getName());
				}



				if (annotationsToView.contains(an))
				{					
					yOffset += addRenderable(annotationDrawMapper.createAnnotationRenderer(this, an, s), an.getName());
				} else {
				
					debug("Sequence Image: Not viewing: " + an.getName());
				}
			}

			if (rnsOfTopRuler != null)
			{
				yOffset += addRenderable(new RulerImpl(this, s, rnsOfTopRuler, true), UPPER_RULER);
			}

			sequenceDrawer = new SequenceDrawer(this, s);
			yOffset += addRenderable(sequenceDrawer, SEQUENCE);

			yOffset += addRenderable(new RulerImpl(this, s, rnsOfBottomRuler, false), LOWER_RULER);

			yOffset += addRenderable(spacer, SPACER);


		}

		if (ptmAnnotationExists) {
			debug("we have a ptm annotation...");
			// collect ptms
			Set<ProteinModification> protMods = new HashSet<ProteinModification>();
			for (Sequence s : sequences) {
				@SuppressWarnings("unchecked")
				ProtModAnnotationGroup clag = (ProtModAnnotationGroup) s.getAnnotationGroup(protModClass);
				debug("got ProtModAnntoationGroup " + clag);
				if (clag == null || !clag.hasData())
					continue;

				for (ModifiedCompound mc : clag.getModCompounds()) {
					debug("got modified compound:  " + mc);
					ProteinModification mod = mc.getModification();
					protMods.add(mod);
				}
			}

			modDrawerUtil = new ProtModDrawerUtil(protMods);
			modDrawerUtil.setCrosslinkLineThickness(getFontSize() * RELATIVE_DISULPHIDE_LINE_THICKNESS);
			modDrawerUtil.setCrosslinkLineBendOffset(fontSize/2);

			ProtModLegendDrawer modLegendDrawer = new ProtModLegendDrawer(modDrawerUtil, getFont(), getImageWidth());
			//			modLegendDrawer.setModDrawerUtil(modDrawerUtil);
			yOffset += addRenderable(modLegendDrawer, "Protein modification");
		}

		// we use the presence of a sequence drawer after going through the
		// loop to determine whether it was successful.
		if (sequenceDrawer != null)
		{
			this.imageHeight = yOffset;
		}
		else
		{
			System.err.println("problem during creation of SequenceImage: sequenceDrawer == null!");
			System.err.println("Uh oh -- the sequence image for " + sequences + " didn't get drawn right");
			this.imageHeight = 0;
		}
	}

	public int addRenderable(Drawer r, String key)
	{
		orderedRenderables.add(r);

		ImageMapData imd = r.getHtmlMapData();


		if (! key.equals(SPACER)) 
			if ( imd == null ) {
				System.err.println("imageMapData == null !" + key);
			}
			else {
				allMaps.put(key, imd);
			}
		return r.getImageHeightPx();
	}




	public BufferedImage getBufferedImage(){

		BufferedImage result = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_4BYTE_ABGR);
		try {
			Graphics2D g2 = result.createGraphics();

			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			g2.setBackground(Color.white);
			g2.clearRect(0, 0, imageWidth, imageHeight);

			Map<ModifiedCompound, List<Point>> crosslinkPoints = new HashMap<ModifiedCompound, List<Point>>();

			int yOffset = 0;

			// iterate through all the drawers, telling them to draw at the given y offset on the graphics object
			for (Drawer r : orderedRenderables)
			{
				if (r instanceof ProtModDrawer) {
					((ProtModDrawer)r).setModDrawerUtil(modDrawerUtil);
					// fudge factor for working out the start y position of the dotted lines
				}

				r.draw(g2, yOffset);
				yOffset += r.getImageHeightPx();

				// also collect the positions of all the crosslinks so we can draw the lines connecting them
				// once we're done with this loop
				if (r instanceof ProtModDrawer)
				{
					Map<ModifiedCompound, List<Point>> map = ((ProtModDrawer)r).getCrosslinkPositions();
					for (Map.Entry<ModifiedCompound, List<Point>> entry : map.entrySet()) {
						ModifiedCompound mc = entry.getKey();
						List<Point> points = crosslinkPoints.get(mc);
						if (points==null) {
							points = new ArrayList<Point>();
							crosslinkPoints.put(mc, points);
						}
						points.addAll(entry.getValue());
					}
				}
			}


			for (Map.Entry<ModifiedCompound, List<Point>> entry : crosslinkPoints.entrySet())
			{
				ModifiedCompound crosslink = entry.getKey();
				List<Point> points = entry.getValue();
				modDrawerUtil.drawCrosslinks(g2, crosslink.getModification(), 
						points);
			}
		} catch (Exception e){
			e.printStackTrace();
		}
		return result;
	}
	/**
	 * <p>
	 * Gets the image encoded as a <tt>byte[]</tt>.
	 * </p>
	 * 
	 * @return
	 */
	public byte[] getImageBytes()
	{
		if (imageBytes == null)
		{
			BufferedImage result = getBufferedImage(); 
			imageBytes = bufferedImageToByteArray(result, imageWidth);
		}
		return imageBytes;
	}

	/**
	 * Get the height of the image in pixels
	 * 
	 * @param key
	 * @return
	 */
	private int getHeightPx(String key)
	{
		ImageMapData data = allMaps.getFirst(key);
		return data == null ? 0 : data.getImageHeightPx();
	}

	/**
	 * 
	 * @return
	 */
	public MapOfCollections<String, ImageMapData> getAllMaps()
	{
		return allMaps;
	}

	private ImageMapData imageMap = null;

	public ImageMapData getImageMap()
	{
		// PdbLogger.warn("in getImageMap " + imageMap + " h:" + getImageHeight());
		if (imageMap == null)
		{
			imageMap = new ImageMapData("chain" + getChainIdsString() + "map", getImageHeight())
			{
				private static final long serialVersionUID = 1L;

				@Override
				public void populateImageMapData()
				{
					int yOffset = 0;// , count = 0;
					for (Drawer d : orderedRenderables)
					{
						try {
							ImageMapData hmd = d.getHtmlMapData();
							if ( hmd == null) {
								System.err.println("SequenceImage: ImageMapData == null : " + getChainIdsString() );
								continue;
							}
							hmd.setYOffset(yOffset);
							addAllImageMapDataEntries(hmd.getImageMapDataEntries());

							yOffset += hmd.getImageHeightPx();
						} catch (Exception e){
							e.printStackTrace();
						}
					}

					yOffset += fragmentBufferPx;
				}
			};
		}

		return imageMap;
	}

	private String getChainIdsString()
	{
		StringBuffer result = new StringBuffer();
		Set<String> chainIds = new HashSet<String>();
		for (Sequence s : sequences)
		{
			chainIds.add(s.getChainId());
		}
		for (String chainId : chainIds)
		{
			result.append(chainId);
		}
		return result.toString();
	}

	public int getRulerHeightPx()
	{
		return getHeightPx(LOWER_RULER);
	}

	public int getSequenceHeightPx()
	{
		return getHeightPx(SEQUENCE);
	}

	public int getAnnotationHeightPx(AnnotationName an)
	{
		return an == null ? 0 : getHeightPx(an.getName());
	}

	public AnnotationDrawMapper getAnnotationDrawMapper() {
		return annotationDrawMapper;
	}

	public void setAnnotationDrawMapper(AnnotationDrawMapper annotationDrawMapper) {
		this.annotationDrawMapper = annotationDrawMapper;
	}
	
	private void debug(String msg){
		if (DEBUG)
			System.out.println(msg);
	}



}
