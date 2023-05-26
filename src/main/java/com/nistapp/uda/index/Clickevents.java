package com.nistapp.uda.index;

import com.nistapp.uda.index.models.*;
import com.nistapp.uda.index.services.Votes;
import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.ConfigProvider;
import org.hibernate.search.mapper.orm.Search;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.sql.Timestamp;
import java.util.*;

@Path("/clickevents")
public class Clickevents {

    private static final Logger logger = LoggerFactory.getLogger(Clickevents.class);

    @Inject
    EntityManager em;

    @GET
    @Path("/all")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public List<JavascriptEvents> listalldata() {
        TypedQuery<JavascriptEvents> query = em.createQuery("select e from JavascriptEvents e", JavascriptEvents.class);
        return query.getResultList();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public JavascriptEvents indexeddata(JavascriptEvents javascriptevents) {
        em.persist(javascriptevents);
        return javascriptevents;
    }

    @GET
    @Path("/fetchbyurl")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public List<JavascriptEvents> listdatabyurl(@QueryParam("url") @DefaultValue("NA") String url) {
        TypedQuery<JavascriptEvents> query = em.createQuery("select  e from JavascriptEvents e where e.urlpath=:url", JavascriptEvents.class).setParameter("url", url);
        return query.getResultList();
    }

    @GET
    @Path("/fetchrecorddata")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public List<Userclicknodes> getuserrecordeddata(@QueryParam("start") long starttimestamp, @QueryParam("end") long endtimestamp, @QueryParam("sessionid") String sessionid, @QueryParam("domain") String domain) {
        TypedQuery<Userclicknodes> query = em.createQuery("select u from Userclicknodes u where u.sessionid=:sessionid and u.domain=:domain and u.createdat between :startdate and :enddate", Userclicknodes.class);
        query.setParameter("sessionid", sessionid).setParameter("domain", domain).setParameter("startdate", starttimestamp).setParameter("enddate", endtimestamp);
        return query.getResultList();
    }

    @GET
    @Path("/fetchtimestamp")
    @Produces(MediaType.TEXT_PLAIN)
    public long gettimestamp() {
        Date date = new Date();
        long time = date.getTime();
        Timestamp ts = new Timestamp(time);
        return time;
    }

    @POST
    @Path("/recordsequencedata")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public SequenceList indexeddata(SequenceList sequenceList) {
        SequenceList s2 = new SequenceList();
        s2.setDomain(sequenceList.getDomain());
        s2.setUsersessionid(sequenceList.getUsersessionid());
        s2.setName(sequenceList.getName());
        s2.setUserclicknodelist(sequenceList.getUserclicknodelist());
        s2.setIsValid(sequenceList.getIsValid());
        s2.setIsIgnored(sequenceList.getIsIgnored());
        s2.setAdditionalParams(sequenceList.getAdditionalParams());
        em.persist(s2);

        Set<Userclicknodes> list = new HashSet<>();
        for (Userclicknodes userclicknodes : sequenceList.getUserclicknodesSet()) {
            Userclicknodes u2 = em.find(Userclicknodes.class, userclicknodes.getId());
            u2.setSequenceList(s2);
            list.add(u2);
        }
        s2.setUserclicknodesSet(list);

        Set<SequenceVotes> votes = new HashSet<>();
        SequenceVotes sequenceVotes = new SequenceVotes();
        sequenceVotes.setSequenceid(s2.getId());
        sequenceVotes.setUsersessionid(s2.getUsersessionid());
        sequenceVotes.setDownvote(0);
        sequenceVotes.setUpvote(0);
        sequenceVotes.persist();

        votes.add(sequenceVotes);
        s2.setSequenceVotes(votes);

        em.persist(s2);
        return s2;
    }

    @Transactional
    void onStart(@Observes StartupEvent event) throws InterruptedException {
        logger.info(ConfigProvider.getConfig().getConfigSources().toString());
        Long value = em.createQuery("SELECT COUNT(s.id) FROM SequenceList s where s.deleted=0 and s.isValid=1 and s.isIgnored=0", Long.class).getSingleResult();
        if (value != null && value != 0) {
            Search.session(em).massIndexer(SequenceList.class).startAndWait();
        }
    }

    @POST
    @Path("sequence/delete")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public SequenceList deletesequence(SequenceList sequenceList) {
        TypedQuery<SequenceList> query = em.createQuery("select sq from SequenceList sq where sq.id=:id", SequenceList.class);
        query.setParameter("id", sequenceList.getId());
        SequenceList dbresult = query.getSingleResult();
        if (dbresult == null)
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        String dbuserid = dbresult.getUsersessionid();
        String sentuserid = sequenceList.getUsersessionid();
        if (dbuserid.equals(sentuserid)) {
            dbresult.setDeleted(1);
            em.persist(dbresult);
            return dbresult;
        } else {
            System.out.println("Not matched user sessionid");
            return null;
        }
    }


    @GET
    @Path("sequence/votes/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<SequenceVotes> getsequencevotes(@PathParam("id") long id) {
        TypedQuery<SequenceVotes> query = em.createQuery("select sqv from SequenceVotes sqv where sqv.sequenceid=:id", SequenceVotes.class);
        query.setParameter("id", id);
        return query.getResultList();
    }

    @PUT
    @Path("userclick")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public void adduserclick(ClickTrack clickTrack) {
        clickTrack.persist();
    }

    @GET
    @Path("/suggested")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Userclicknodes> suggestSequenceList(@QueryParam("domain") String domain) {
        List<Integer> nodeids = getrandomnumber();
        List<Userclicknodes> userclicknodes = new ArrayList<>();

        TypedQuery<Userclicknodes> query = em.createQuery("select u from Userclicknodes u where u.id in :id and u.domain=:domain", Userclicknodes.class);
        query.setParameter("domain", domain);
        query.setParameter("id", nodeids);
        userclicknodes = query.getResultList();

        return userclicknodes;
    }

    public static List<Integer> getrandomnumber() {

        Random rand = new Random();

        Integer generatelength = rand.nextInt((10 - 3) + 1) + 3;

        List<Integer> intlist = new ArrayList<>();

        for (int i = 0; i < generatelength; i++) {
            intlist.add(rand.nextInt(100));
        }

        return intlist;
    }
}
