package com.headroomai.farmwatch.potion;

import com.headroomai.farmwatch.potion.HerbVarbitDecoder.CropState;
import com.headroomai.farmwatch.potion.HerbVarbitDecoder.Decoded;
import com.headroomai.farmwatch.potion.HerbVarbitDecoder.Produce;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HerbVarbitDecoderTest
{
    @Test
    public void weedsZeroDecodesAsFreshlyWeeded()
    {
        Decoded d = HerbVarbitDecoder.decode(0);
        assertNotNull(d);
        assertEquals(Produce.WEEDS, d.produce);
        assertEquals(CropState.GROWING, d.state);
        assertFalse(d.isSpecificHerb());
    }

    @Test
    public void ranarrGrowingStagesSpanFourValues()
    {
        for (int v = 32; v <= 35; v++)
        {
            Decoded d = HerbVarbitDecoder.decode(v);
            assertNotNull("v=" + v, d);
            assertEquals("v=" + v, Produce.RANARR, d.produce);
            assertEquals(CropState.GROWING, d.state);
            assertEquals(v - 32, d.stage);
        }
    }

    @Test
    public void ranarrHarvestableIs36To38()
    {
        Decoded a = HerbVarbitDecoder.decode(36);
        Decoded b = HerbVarbitDecoder.decode(38);
        assertEquals(Produce.RANARR, a.produce);
        assertEquals(CropState.HARVESTABLE, a.state);
        assertEquals(Produce.RANARR, b.produce);
        assertEquals(CropState.HARVESTABLE, b.state);
    }

    @Test
    public void torstolBoundariesLineUp()
    {
        // 103-106 growing, 107-109 harvestable.
        assertEquals(Produce.TORSTOL, HerbVarbitDecoder.decode(103).produce);
        assertEquals(CropState.GROWING, HerbVarbitDecoder.decode(103).state);
        assertEquals(Produce.TORSTOL, HerbVarbitDecoder.decode(109).produce);
        assertEquals(CropState.HARVESTABLE, HerbVarbitDecoder.decode(109).state);
    }

    @Test
    public void deadIsAnyHerb()
    {
        Decoded d = HerbVarbitDecoder.decode(170);
        assertNotNull(d);
        assertEquals(Produce.ANYHERB, d.produce);
        assertEquals(CropState.DEAD, d.state);
        assertFalse(d.isSpecificHerb());
    }

    @Test
    public void diseasedRanarrDecodes()
    {
        Decoded d = HerbVarbitDecoder.decode(141);
        assertEquals(Produce.RANARR, d.produce);
        assertEquals(CropState.DISEASED, d.state);
    }

    @Test
    public void huascaIsInItsOwnBand()
    {
        // Huasca grows at 60-63, harvests at 64-66, diseased at 173-175.
        assertEquals(Produce.HUASCA, HerbVarbitDecoder.decode(60).produce);
        assertEquals(CropState.GROWING, HerbVarbitDecoder.decode(60).state);
        assertEquals(Produce.HUASCA, HerbVarbitDecoder.decode(174).produce);
        assertEquals(CropState.DISEASED, HerbVarbitDecoder.decode(174).state);
    }

    @Test
    public void varbit67IsEmptyWeeds()
    {
        Decoded d = HerbVarbitDecoder.decode(67);
        assertEquals(Produce.WEEDS, d.produce);
        assertEquals(3, d.stage);
    }

    @Test
    public void outOfRangeVarbitDecodesToNull()
    {
        // 110-127 is a gap between harvestable torstol and diseased guam.
        assertNull(HerbVarbitDecoder.decode(120));
        assertNull(HerbVarbitDecoder.decode(-1));
        assertNull(HerbVarbitDecoder.decode(500));
    }

    @Test
    public void everyRecognizedVarbitIsSelfConsistent()
    {
        // The HERB decoder covers 0-191 with a few gaps (110-127, 192+). For
        // every value that returns non-null, the stage must be in [0, 15]
        // (16 was observed in the WEEDS filler band 176-191 but it's clamped
        // to stage=3). This is a sanity check against copy-paste bugs in the
        // big if-chain.
        for (int v = 0; v <= 220; v++)
        {
            Decoded d = HerbVarbitDecoder.decode(v);
            if (d == null) continue;
            assertTrue("v=" + v + " stage=" + d.stage, d.stage >= 0 && d.stage <= 15);
            assertNotNull("v=" + v + " produce null", d.produce);
            assertNotNull("v=" + v + " state null", d.state);
        }
    }

    @Test
    public void herbPatchVarbitWhitelistIsRecognized()
    {
        for (int id : new int[]{4771, 4772, 4773, 4774, 4775, 7904, 7905, 7906, 7907, 10981})
        {
            assertTrue("expected herb varbit " + id, HerbVarbitDecoder.isKnownHerbPatchVarbitId(id));
        }
        assertFalse("random non-herb varbit", HerbVarbitDecoder.isKnownHerbPatchVarbitId(12345));
    }

    @Test
    public void produceForMapsIngredientToDecoderProduce()
    {
        assertEquals(Produce.RANARR, HerbVarbitDecoder.produceFor(Ingredient.RANARR));
        assertEquals(Produce.TORSTOL, HerbVarbitDecoder.produceFor(Ingredient.TORSTOL));
        assertNull(HerbVarbitDecoder.produceFor(Ingredient.EYE_OF_NEWT));
        assertNull(HerbVarbitDecoder.produceFor(null));
    }

    @Test
    public void everyHerbIngredientHasDecoderProduce()
    {
        for (Ingredient ing : Ingredient.values())
        {
            if (ing.type() == Ingredient.Type.HERB)
            {
                assertNotNull("no decoder produce mapping for " + ing,
                    HerbVarbitDecoder.produceFor(ing));
            }
        }
    }
}
